package com.breno.mpp_converter.controller;

import com.breno.mpp_converter.service.OdooService;
import net.sf.mpxj.ProjectFile;
import net.sf.mpxj.Task;
import net.sf.mpxj.reader.ProjectReader;
import net.sf.mpxj.reader.ProjectReaderUtility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class MppController {

    @Autowired
    private OdooService odooService;

    // Define o formato de data que o Odoo aceita
    private final SimpleDateFormat odooDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @PostMapping("/create-odoo-project")
    public ResponseEntity<String> createOdooProjectFromFile(@RequestParam("file") MultipartFile multipartFile) {
        if (multipartFile.isEmpty() || multipartFile.getOriginalFilename() == null
                || !multipartFile.getOriginalFilename().endsWith(".mpp")) {
            return ResponseEntity.badRequest().body("Arquivo inválido ou não é .mpp");
        }

        File file = null;
        try {
            // 1. Ler o arquivo MPP
            file = File.createTempFile("temp-mpp-", ".mpp");
            multipartFile.transferTo(file);
            ProjectReader reader = ProjectReaderUtility.getProjectReader(file.getAbsolutePath());
            ProjectFile projectFile = reader.read(file.getAbsolutePath());

            // 2. Conectar-se ao Odoo
            odooService.connect();

            // 3. Mapa para relacionar ID do MPP -> ID do Odoo
            Map<Integer, Integer> mppToOdooIdMap = new HashMap<>();

            // 4. Criar o Projeto Principal
            // A tarefa do projeto é a primeira filha da tarefa raiz (ID 0)
            Task projectHeaderTask = projectFile.getTasks().stream()
                    .filter(t -> t.getParentTask() != null && t.getParentTask().getUniqueID() == 0)
                    .findFirst()
                    .orElseThrow(
                            () -> new RuntimeException("Tarefa do projeto principal (filha do ID 0) não encontrada."));

            Map<String, Object> projectValues = new HashMap<>();
            projectValues.put("name", projectHeaderTask.getName());
            int odooProjectId = odooService.create("project.project", projectValues);

            // Colocamos o ID do projeto no mapa para as tarefas de primeiro nível se
            // conectarem a ele
            mppToOdooIdMap.put(projectHeaderTask.getUniqueID(), odooProjectId);
            System.out.println("Projeto '" + projectHeaderTask.getName() + "' criado com ID Odoo: " + odooProjectId);

            // 5. Lógica de Passes para criar tarefas e subtarefas
            // A chave aqui é ordenar as tarefas por "nível de estrutura".
            // Isso garante que uma tarefa-mãe sempre será processada antes de suas filhas.
            List<Task> sortedTasks = projectFile.getTasks().stream()
                    .filter(t -> t.getUniqueID() != 0 && t.getUniqueID() != projectHeaderTask.getUniqueID()) // Ignora o
                                                                                                             // root e o
                                                                                                             // próprio
                                                                                                             // projeto
                    .sorted((t1, t2) -> Integer.compare(t1.getOutlineLevel(), t2.getOutlineLevel()))
                    .collect(Collectors.toList());

            System.out.println("Iniciando criação de " + sortedTasks.size() + " tarefas...");

            for (Task task : sortedTasks) {
                Map<String, Object> taskValues = new HashMap<>();
                taskValues.put("name", task.getName());
                taskValues.put("project_id", odooProjectId); // Todas as tarefas pertencem a este projeto

                // Formata a data de prazo final
                if (task.getFinish() != null) {
                    taskValues.put("date_deadline", odooDateFormat.format(task.getFinish()));
                }

                // Define a hierarquia (tarefa pai)
                if (task.getParentTask() != null) {
                    // Pega o ID MPP da tarefa-mãe
                    Integer parentMppId = task.getParentTask().getUniqueID();
                    // Procura no nosso mapa o ID correspondente no Odoo
                    Integer parentOdooId = mppToOdooIdMap.get(parentMppId);

                    // Se encontrarmos o pai no mapa (ele já foi criado), definimos a relação
                    if (parentOdooId != null) {
                        // Para tarefas de primeiro nível, o pai será o projeto (e o campo é
                        // 'project_id', já setado)
                        // Para subtarefas, o pai é outra tarefa, e o campo é 'parent_id'.
                        // A tarefa-mãe de uma subtarefa não pode ser o próprio projeto.
                        if (task.getParentTask().getUniqueID() != projectHeaderTask.getUniqueID()) {
                            taskValues.put("parent_id", parentOdooId);
                        }
                    }
                }

                // Cria a tarefa no Odoo
                int newOdooTaskId = odooService.create("project.task", taskValues);
                // Adiciona a tarefa recém-criada ao nosso mapa para que suas filhas possam
                // encontrá-la
                mppToOdooIdMap.put(task.getUniqueID(), newOdooTaskId);
                System.out.println("  - Tarefa '" + task.getName() + "' (MPP ID: " + task.getUniqueID()
                        + ") criada com ID Odoo: " + newOdooTaskId);
            }

            return ResponseEntity.ok("Projeto e tarefas criados com sucesso no Odoo! ID do Projeto: " + odooProjectId);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro: " + e.getMessage());
        } finally {
            if (file != null) {
                file.delete();
            }
        }
    }
}
