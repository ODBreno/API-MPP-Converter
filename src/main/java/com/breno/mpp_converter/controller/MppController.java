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
import java.io.FileOutputStream;
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
        if (multipartFile.isEmpty() || !multipartFile.getOriginalFilename().endsWith(".mpp")) {
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
            Task projectTask = projectFile.getTasks().stream()
                    .filter(t -> t.getParentTask() != null && t.getParentTask().getUniqueID() == 0)
                    .findFirst()
                    .orElseThrow(
                            () -> new RuntimeException("Tarefa do projeto principal (filha do ID 0) não encontrada."));

            Map<String, Object> projectValues = new HashMap<>();
            projectValues.put("name", projectTask.getName());
            int odooProjectId = odooService.create("project.project", projectValues);
            mppToOdooIdMap.put(projectTask.getUniqueID(), odooProjectId); // Adiciona o próprio projeto ao mapa
            System.out.println("Projeto '" + projectTask.getName() + "' criado com ID: " + odooProjectId);

            // 5. Lógica de Passes para criar tarefas e subtarefas
            // Ordenamos por nível para garantir que os pais sejam criados antes dos filhos
            List<Task> sortedTasks = projectFile.getTasks().stream()
                    .filter(t -> t.getUniqueID() != 0 && t.getUniqueID() != projectTask.getUniqueID()) // Ignora o root
                                                                                                       // e o projeto
                    .sorted((t1, t2) -> Integer.compare(t1.getOutlineLevel(), t2.getOutlineLevel()))
                    .collect(Collectors.toList());

            for (Task task : sortedTasks) {
                Map<String, Object> taskValues = new HashMap<>();
                taskValues.put("name", task.getName());
                taskValues.put("project_id", odooProjectId); // Todas as tarefas pertencem a este projeto

                // Formata as datas para o padrão do Odoo
                if (task.getFinish() != null) {
                    taskValues.put("date_deadline", odooDateFormat.format(task.getFinish()));
                }
                if (task.getStart() != null) {
                    taskValues.put("date_start", odooDateFormat.format(task.getStart()));
                }

                // Define a hierarquia (tarefa pai)
                if (task.getParentTask() != null) {
                    Integer parentOdooId = mppToOdooIdMap.get(task.getParentTask().getUniqueID());
                    if (parentOdooId != null) {
                        // O parentId de uma tarefa no Odoo é o ID da tarefa pai, não do projeto.
                        taskValues.put("parent_id", parentOdooId);
                    }
                }

                // Cria a tarefa no Odoo
                int newOdooTaskId = odooService.create("project.task", taskValues);
                mppToOdooIdMap.put(task.getUniqueID(), newOdooTaskId); // Salva o novo mapeamento
                System.out.println("  - Tarefa '" + task.getName() + "' criada com ID: " + newOdooTaskId);
            }

            // Aqui seria o passo para criar as dependências (predecessors), se necessário.

            return ResponseEntity.ok("Projeto e tarefas criados com sucesso no Odoo! ID do Projeto: " + odooProjectId);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erro: " + e.getMessage());
        } finally {
            if (file != null && file.exists()) {
                file.delete();
            }
        }
    }
}
