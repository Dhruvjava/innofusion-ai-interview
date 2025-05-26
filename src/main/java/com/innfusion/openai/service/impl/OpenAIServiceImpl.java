package com.innfusion.openai.service.impl;

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
import org.springframework.ai.openai.audio.speech.Speech;
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
                throw new RuntimeException("Questions are empty.");
            }
            List<byte[]> audioBytes = new ArrayList<>();
            for (String question : questions) {
                try {
                    byte[] speech = openAiAudioSpeechModel.call(question);
                    audioBytes.add(speech);
                } catch (Exception speechEx) {
                    log.error("Error generating audio for question: '" + question + "'. Error: "
                                    + speechEx.getMessage());
                    throw new RuntimeException("Failed to generate audio for: " + question,
                                    speechEx);
                }

            }
            return audioBytes;
        } catch (Exception e) {
            log.error("Exception in generateTextToAudio(List<String> questions) -> {}",
                            e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }
}
