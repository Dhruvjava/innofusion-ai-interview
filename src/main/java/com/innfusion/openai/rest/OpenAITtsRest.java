package com.innfusion.openai.rest;

import com.innfusion.openai.rq.OpenAITtsRq;
import com.innfusion.openai.service.OpenAIService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tts")
@Slf4j
@RequiredArgsConstructor
public class OpenAITtsRest {

    private final OpenAIService openAIService;

    @PostMapping("/generate")
    public ResponseEntity<List<byte[]>> generateTextToSpeech(@RequestBody OpenAITtsRq openAITtsRq) {
        if (log.isDebugEnabled()) {
            log.debug("Executing RestFull Services : [ POST: /api/v1/tts/generate ] -> ");
        }
        try {

            return ResponseEntity.ok(openAIService.generateTextToAudio(openAITtsRq.getQuestions()));
        } catch (Exception e) {
            log.error("Exception in RestFull Services : [ POST: /api/v1/tts/generate ] -> {}",
                            e.getMessage());
            throw e;
        }
    }

}
