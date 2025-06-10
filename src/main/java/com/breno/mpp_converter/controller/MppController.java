package com.breno.mpp_converter.controller;

import com.breno.mpp_converter.dto.TaskDTO;
import net.sf.mpxj.ProjectFile;
import net.sf.mpxj.Relation;
import net.sf.mpxj.Task;
import net.sf.mpxj.reader.ProjectReader;
import net.sf.mpxj.reader.ProjectReaderUtility;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class MppController {

    @PostMapping("/mpp-to-json")
    public ResponseEntity<List<TaskDTO>> convertMppToJson(@RequestParam("file") MultipartFile multipartFile) {
        if (multipartFile.isEmpty() || multipartFile.getOriginalFilename() == null
                || !multipartFile.getOriginalFilename().endsWith(".mpp")) {
            return ResponseEntity.badRequest().build();
        }

        File file = null;
        try {
            file = File.createTempFile("temp-mpp-", ".mpp");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(multipartFile.getBytes());
            }

            ProjectReader reader = ProjectReaderUtility.getProjectReader(file.getAbsolutePath());
            ProjectFile project = reader.read(file.getAbsolutePath());

            List<TaskDTO> taskList = new ArrayList<>();
            for (Task task : project.getTasks()) {
                TaskDTO dto = new TaskDTO();
                dto.setId(task.getUniqueID());
                dto.setName(task.getName());
                dto.setStartDate(task.getStart());
                dto.setFinishDate(task.getFinish());
                dto.setDuration(task.getDuration().toString());
                dto.setPercentageComplete(
                        task.getPercentageComplete() != null ? task.getPercentageComplete().doubleValue() : 0.0);

                if (task.getPredecessors() != null) {
                    List<Integer> predecessorIds = task.getPredecessors().stream()
                            .map(Relation::getTargetTask)
                            .map(Task::getUniqueID)
                            .collect(Collectors.toList());
                    dto.setPredecessors(predecessorIds);
                }

                Task parentTask = task.getParentTask();
                if (parentTask != null) {
                    dto.setParentId(parentTask.getUniqueID());
                }

                taskList.add(dto);
            }
            return ResponseEntity.ok(taskList);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } finally {
            if (file != null && file.exists()) {
                file.delete();
            }
        }
    }
}