package com.innfusion.openai.rest;

import com.innfusion.base.BaseDataRs;
import com.innfusion.openai.service.VideoStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/video")
@RequiredArgsConstructor
public class VideoStreamRest {

    private final VideoStreamService service;

    @PostMapping
    public ResponseEntity<BaseDataRs> uploadVideo(@RequestParam MultipartFile file,
                    @RequestParam String intervieweeId,
                    @RequestParam String questionId,
                    @RequestParam String question
    ){
        return ResponseEntity.ok(service.processVideoStream(file, question, intervieweeId, questionId));
    }

}
