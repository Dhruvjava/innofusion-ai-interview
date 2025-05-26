package com.innfusion.openai.service;

import java.util.List;

public interface OpenAIService {

    public List<byte[]>generateTextToAudio(List<String> questions);

}
