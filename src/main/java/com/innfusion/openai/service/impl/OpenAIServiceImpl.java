package com.innfusion.openai.service.impl;

import com.innfusion.openai.exception.OpenAiAuthenticationException;
import com.innfusion.openai.exception.OpenAiQuotaException;
import com.innfusion.openai.service.OpenAIService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions;
import org.springframework.ai.openai.api.OpenAiAudioApi.SpeechRequest;
import org.springframework.ai.openai.api.common.OpenAiApiClientErrorException;
import org.springframework.ai.openai.audio.speech.Speech;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenAIServiceImpl implements OpenAIService {

    private final OpenAiAudioSpeechModel openAiAudioSpeechModel;

    @Override
    public List<byte[]> generateTextToAudio(List<String> questions) {
        if (log.isDebugEnabled()) {
            log.debug("Executing generateTextToAudio(List<String> questions) -> ");
        }
        try {
            if (CollectionUtils.isEmpty(questions)) {
                throw new IllegalArgumentException("Questions are empty.");
            }
            List<byte[]> audioBytes = new ArrayList<>();
            for (String question : questions) {
                try {
                    log.info("Generating Audio for Question : {}", question);
                    byte[] speech = openAiAudioSpeechModel.call(question);
                    audioBytes.add(speech);
                    log.info("Successfully Generated Audio for Question : {}", question);
                } catch (NonTransientAiException aiException) {
                    log.error("Exception While generating Audio for question : {}, Error message : {}",
                                    question, aiException.getMessage());
                    log.info(aiException.getLocalizedMessage());
                    if (aiException.getMessage() != null && aiException.getMessage()
                                    .contains("insufficient_quota")) {
                        log.warn("OpenAI Quota Exceeded (caught as NonTransientAiException): {}",
                                        aiException.getMessage());
                        throw new OpenAiQuotaException(aiException.getMessage());
                    } else if (aiException.getMessage() != null && aiException.getMessage().toUpperCase()
                                    .contains("HTTP 401")) {
                        log.error("OpenAI Authentication Error: {}", aiException.getMessage());
                        throw new OpenAiAuthenticationException(aiException.getMessage());
                    }
                } catch (Exception speechEx) {
                    log.error("Error generating audio for question: '" + question + "'. Error: "
                                    + speechEx.getMessage());
                    throw new RuntimeException("Failed to generate audio for: " + question,
                                    speechEx);
                }

            }
            log.info("Successfully generated audio for all questions : {}", questions.size());
            return audioBytes;
        } catch (Exception e) {
            log.error("Exception in generateTextToAudio(List<String> questions) -> {}",
                            e.getMessage());
            throw e;
        }
    }
}
