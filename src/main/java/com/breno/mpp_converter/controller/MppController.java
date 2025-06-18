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
    public ResponseEntity<?> createOdooProjectFromFile(@RequestParam("file") MultipartFile multipartFile) {
        if (multipartFile.isEmpty() || multipartFile.getOriginalFilename() == null
                || !multipartFile.getOriginalFilename().endsWith(".mpp")) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Arquivo inválido ou não é .mpp");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        File file = null;
        try {
            // 1. Ler o arquivo MPP e Conectar ao Odoo
            file = File.createTempFile("temp-mpp-", ".mpp");
            multipartFile.transferTo(file);
            ProjectReader reader = ProjectReaderUtility.getProjectReader(file.getAbsolutePath());
            ProjectFile projectFile = reader.read(file.getAbsolutePath());
            odooService.connect();

            // 2. Mapa para relacionar ID do MPP -> ID do Odoo
            Map<Integer, Integer> mppToOdooIdMap = new HashMap<>();

            // 3. Criar o Projeto Principal
            Task projectHeaderTask = projectFile.getTasks().stream()
                    .filter(t -> t.getParentTask() != null && t.getParentTask().getUniqueID() == 0)
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Tarefa do projeto principal não encontrada."));

            Map<String, Object> projectValues = new HashMap<>();
            projectValues.put("name", projectHeaderTask.getName());
            int odooProjectId = odooService.create("project.project", projectValues);
            mppToOdooIdMap.put(projectHeaderTask.getUniqueID(), odooProjectId);
            System.out.println("Projeto '" + projectHeaderTask.getName() + "' criado com ID Odoo: " + odooProjectId);

            System.out.println("Criando estágios para o projeto...");
            Integer plannedStageId = null; // Vamos guardar o ID do estágio "Planejadas"
            List<String> stageNames = Arrays.asList("Planejadas", "Em andamento", "Em revisão", "Concluídas", "Outras");

            for (String stageName : stageNames) {
                Map<String, Object> stageValues = new HashMap<>();
                stageValues.put("name", stageName);
                // Associa o estágio ao projeto que acabamos de criar
                stageValues.put("project_ids",
                        Collections.singletonList(Arrays.asList(6, 0, Collections.singletonList(odooProjectId))));

                int newStageId = odooService.create("project.task.type", stageValues);
                System.out.println("  - Estágio '" + stageName + "' criado com ID: " + newStageId);

                // Se este for o estágio "Planejadas", guardamos o ID dele
                if ("Planejadas".equals(stageName)) {
                    plannedStageId = newStageId;
                }
            }
            // 4. Lógica para criar tarefas e subtarefas
            List<Task> sortedTasks = projectFile.getTasks().stream()
                    .filter(t -> t.getUniqueID() != 0 && t.getUniqueID() != projectHeaderTask.getUniqueID())
                    .sorted((t1, t2) -> Integer.compare(t1.getOutlineLevel(), t2.getOutlineLevel()))
                    .collect(Collectors.toList());

            System.out.println("Iniciando criação de " + sortedTasks.size() + " tarefas...");

            for (Task task : sortedTasks) {
                Map<String, Object> taskValues = new HashMap<>();
                taskValues.put("name", task.getName());
                taskValues.put("project_id", odooProjectId);

                // Associa a tarefa ao estágio "Planejadas"
                if (plannedStageId != null) {
                    taskValues.put("stage_id", plannedStageId);
                }

                if (task.getFinish() != null) {
                    taskValues.put("date_deadline", odooDateFormat.format(task.getFinish()));
                }

                if (task.getParentTask() != null) {
                    Integer parentMppId = task.getParentTask().getUniqueID();
                    Integer parentOdooId = mppToOdooIdMap.get(parentMppId);
                    if (parentOdooId != null && task.getParentTask().getUniqueID() != projectHeaderTask.getUniqueID()) {
                        taskValues.put("parent_id", parentOdooId);
                    }
                }

                int newOdooTaskId = odooService.create("project.task", taskValues);
                mppToOdooIdMap.put(task.getUniqueID(), newOdooTaskId);
                System.out.println(
                        "  - Tarefa '" + task.getName() + "' criada no estágio correto com ID Odoo: " + newOdooTaskId);
            }

            // 5. Lógica para criar dependências
            System.out.println("\nIniciando criação de dependências entre tarefas...");
            for (Task task : sortedTasks) {
                List<Relation> predecessors = task.getPredecessors();
                if (predecessors != null && !predecessors.isEmpty()) {
                    List<Integer> predecessorOdooIds = predecessors.stream()
                            .map(relation -> relation.getTargetTask().getUniqueID())
                            .map(mppToOdooIdMap::get)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());

                    if (!predecessorOdooIds.isEmpty()) {
                        Integer currentOdooId = mppToOdooIdMap.get(task.getUniqueID());
                        Map<String, Object> dependencyValues = new HashMap<>();
                        List<Object> odooCommand = Arrays.asList(6, 0, predecessorOdooIds);
                        dependencyValues.put("depend_on_ids", Collections.singletonList(odooCommand));
                        odooService.update("project.task", currentOdooId, dependencyValues);
                        System.out.println("   -> Dependências definidas para a tarefa '" + task.getName() + "'");
                    }
                }
            }

            Map<String, Object> successResponse = new HashMap<>();
            successResponse.put("status", "sucesso");
            successResponse.put("message", "Projeto, estágios, tarefas e dependências criados com sucesso!");
            successResponse.put("odooProjectId", odooProjectId);

            return ResponseEntity.ok(successResponse);

        } catch (Exception e) {
            e.printStackTrace();
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("status", "erro");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        } finally {
            if (file != null) {
                file.delete();
            }
        }
    }
}
