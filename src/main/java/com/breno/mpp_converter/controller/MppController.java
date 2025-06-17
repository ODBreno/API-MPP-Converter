package com.breno.mpp_converter.controller;

import com.breno.mpp_converter.service.OdooService;
import net.sf.mpxj.ProjectFile;
import net.sf.mpxj.Relation;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
            Task projectHeaderTask = projectFile.getTasks().stream()
                    .filter(t -> t.getParentTask() != null && t.getParentTask().getUniqueID() == 0)
                    .findFirst()
                    .orElseThrow(
                            () -> new RuntimeException("Tarefa do projeto principal (filha do ID 0) não encontrada."));

            Map<String, Object> projectValues = new HashMap<>();
            projectValues.put("name", projectHeaderTask.getName());
            int odooProjectId = odooService.create("project.project", projectValues);

            mppToOdooIdMap.put(projectHeaderTask.getUniqueID(), odooProjectId);
            System.out.println("Projeto '" + projectHeaderTask.getName() + "' criado com ID Odoo: " + odooProjectId);

            // 5. Lógica para criar tarefas e subtarefas, garantindo a ordem
            List<Task> sortedTasks = projectFile.getTasks().stream()
                    .filter(t -> t.getUniqueID() != 0 && t.getUniqueID() != projectHeaderTask.getUniqueID())
                    .sorted((t1, t2) -> Integer.compare(t1.getOutlineLevel(), t2.getOutlineLevel()))
                    .collect(Collectors.toList());

            System.out.println("Iniciando criação de " + sortedTasks.size() + " tarefas...");

            for (Task task : sortedTasks) {
                Map<String, Object> taskValues = new HashMap<>();
                taskValues.put("name", task.getName());
                taskValues.put("project_id", odooProjectId);

                if (task.getFinish() != null) {
                    taskValues.put("date_deadline", odooDateFormat.format(task.getFinish()));
                }

                if (task.getParentTask() != null) {
                    Integer parentMppId = task.getParentTask().getUniqueID();
                    Integer parentOdooId = mppToOdooIdMap.get(parentMppId);
                    if (parentOdooId != null) {
                        if (task.getParentTask().getUniqueID() != projectHeaderTask.getUniqueID()) {
                            taskValues.put("parent_id", parentOdooId);
                        }
                    }
                }

                int newOdooTaskId = odooService.create("project.task", taskValues);
                mppToOdooIdMap.put(task.getUniqueID(), newOdooTaskId);
                System.out.println("  - Tarefa '" + task.getName() + "' (MPP ID: " + task.getUniqueID()
                        + ") criada com ID Odoo: " + newOdooTaskId);
            }
            // 6. Criar dependências entre tarefas
            System.out.println("\nIniciando criação de dependências entre tarefas...");
            for (Task task : sortedTasks) {
                // Pega a lista de predecessoras do arquivo MPP
                List<Relation> predecessors = task.getPredecessors();
                if (predecessors != null && !predecessors.isEmpty()) {

                    // Traduz os IDs das predecessoras do MPP para os IDs do Odoo usando nosso mapa
                    List<Integer> predecessorOdooIds = predecessors.stream()
                            .map(relation -> relation.getTargetTask().getUniqueID())
                            .map(mppToOdooIdMap::get)
                            .filter(Objects::nonNull) // Garante que apenas IDs mapeados sejam incluídos
                            .collect(Collectors.toList());

                    // Se encontramos alguma dependência válida...
                    if (!predecessorOdooIds.isEmpty()) {
                        Integer currentOdooId = mppToOdooIdMap.get(task.getUniqueID());

                        Map<String, Object> dependencyValues = new HashMap<>();
                        // O Odoo espera um comando especial para atualizar campos Many2many: (6, 0,
                        // [lista de IDs])
                        // Este comando significa "substitua a lista atual por esta nova lista".
                        List<Object> odooCommand = Arrays.asList(6, 0, predecessorOdooIds);

                        // O campo padrão no Odoo para dependências é 'depend_on_ids'
                        dependencyValues.put("depend_on_ids", Collections.singletonList(odooCommand));

                        // Atualiza a tarefa no Odoo com suas dependências
                        odooService.update("project.task", currentOdooId, dependencyValues);
                        System.out.println("   -> Dependências definidas para a tarefa '" + task.getName()
                                + "' (ID Odoo: " + currentOdooId + ")");
                    }
                }
            }

            return ResponseEntity.ok(
                    "Projeto, tarefas, hierarquia e dependências criados com sucesso! ID do Projeto: " + odooProjectId);

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
