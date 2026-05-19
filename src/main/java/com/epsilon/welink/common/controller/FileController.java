package com.epsilon.welink.common.controller;

import com.epsilon.welink.common.result.Result;
import com.epsilon.welink.common.service.FileStorageService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/file")
public class FileController {

    private final FileStorageService fileStorageService;

    public FileController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @PostMapping("/upload")
    public Result<String> uploadFile(@RequestParam("file") MultipartFile file) {
        String url = fileStorageService.uploadFile(file);
        return Result.success(url);
    }
}
