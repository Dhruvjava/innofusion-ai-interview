package com.innfusion.openai.service.impl;

import com.innfusion.openai.exception.OpenAiAuthenticationException;
import com.innfusion.openai.exception.OpenAiQuotaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.openai.OpenAiAudioSpeechModel;
import org.springframework.ai.retry.NonTransientAiException;

import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OpenAI Service Implementation Tests")
class OpenAIServiceImplTest {

    @Mock
    private OpenAiAudioSpeechModel openAiAudioSpeechModel;

    @InjectMocks
    private OpenAIServiceImpl openAIService;

    private List<String> testQuestions;
    private byte[] mockAudioBytes;
    private String expectedBase64Audio;

    @BeforeEach
    void setUp() {
        testQuestions = Arrays.asList(
                "What is your experience with Java?",
                "Describe your problem-solving approach.",
                "How do you handle team conflicts?"
        );
        
        // Mock audio bytes
        mockAudioBytes = "test-audio-data".getBytes();
        expectedBase64Audio = Base64.getEncoder().encodeToString(mockAudioBytes);
    }

    @Test
    @DisplayName("Should successfully generate audio for valid questions")
    void generateTextToAudio_WithValidQuestions_ShouldReturnBase64EncodedAudioList() {
        // Given
        when(openAiAudioSpeechModel.call(anyString())).thenReturn(mockAudioBytes);

        // When
        List<String> result = openAIService.generateTextToAudio(testQuestions);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(3);
        assertThat(result).allMatch(audio -> audio.equals(expectedBase64Audio));
        
        // Verify interactions
        verify(openAiAudioSpeechModel, times(3)).call(anyString());
        testQuestions.forEach(question -> 
            verify(openAiAudioSpeechModel).call(question)
        );
    }

    @Test
    @DisplayName("Should successfully generate audio for single question")
    void generateTextToAudio_WithSingleQuestion_ShouldReturnSingleBase64Audio() {
        // Given
        List<String> singleQuestion = Collections.singletonList("Tell me about yourself.");
        when(openAiAudioSpeechModel.call(anyString())).thenReturn(mockAudioBytes);

        // When
        List<String> result = openAIService.generateTextToAudio(singleQuestion);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(expectedBase64Audio);
        verify(openAiAudioSpeechModel, times(1)).call("Tell me about yourself.");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when questions list is null")
    void generateTextToAudio_WithNullQuestions_ShouldThrowIllegalArgumentException() {
        // When & Then
        assertThatThrownBy(() -> openAIService.generateTextToAudio(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Questions are empty.");
        
        verify(openAiAudioSpeechModel, never()).call(anyString());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when questions list is empty")
    void generateTextToAudio_WithEmptyQuestions_ShouldThrowIllegalArgumentException() {
        // Given
        List<String> emptyQuestions = Collections.emptyList();

        // When & Then
        assertThatThrownBy(() -> openAIService.generateTextToAudio(emptyQuestions))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Questions are empty.");
        
        verify(openAiAudioSpeechModel, never()).call(anyString());
    }

    @Test
    @DisplayName("Should throw OpenAiQuotaException when insufficient quota error occurs")
    void generateTextToAudio_WithInsufficientQuota_ShouldThrowOpenAiQuotaException() {
        // Given
        String quotaErrorMessage = "HTTP 429 - insufficient_quota: You exceeded your current quota";
        NonTransientAiException quotaException = new NonTransientAiException(quotaErrorMessage);
        when(openAiAudioSpeechModel.call(anyString())).thenThrow(quotaException);

        // When & Then
        assertThatThrownBy(() -> openAIService.generateTextToAudio(testQuestions))
                .isInstanceOf(OpenAiQuotaException.class);
        
        verify(openAiAudioSpeechModel, times(1)).call(testQuestions.get(0));
    }

    @Test
    @DisplayName("Should throw OpenAiAuthenticationException when HTTP 401 error occurs")
    void generateTextToAudio_WithAuthenticationError_ShouldThrowOpenAiAuthenticationException() {
        // Given
        String authErrorMessage = "HTTP 401 - Unauthorized: Invalid API key";
        NonTransientAiException authException = new NonTransientAiException(authErrorMessage);
        when(openAiAudioSpeechModel.call(anyString())).thenThrow(authException);

        // When & Then
        assertThatThrownBy(() -> openAIService.generateTextToAudio(testQuestions))
                .isInstanceOf(OpenAiAuthenticationException.class);
        
        verify(openAiAudioSpeechModel, times(1)).call(testQuestions.get(0));
    }

    @Test
    @DisplayName("Should wrap generic exceptions in RuntimeException")
    void generateTextToAudio_WithGenericException_ShouldThrowRuntimeException() {
        // Given
        String question = "What is your experience?";
        List<String> singleQuestion = Collections.singletonList(question);
        RuntimeException genericException = new RuntimeException("Generic error occurred");
        when(openAiAudioSpeechModel.call(anyString())).thenThrow(genericException);

        // When & Then
        assertThatThrownBy(() -> openAIService.generateTextToAudio(singleQuestion))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to generate audio for: " + question)
                .hasCause(genericException);
        
        verify(openAiAudioSpeechModel, times(1)).call(question);
    }

    @Test
    @DisplayName("Should handle mixed success and failure scenarios appropriately")
    void generateTextToAudio_WithMixedScenarios_ShouldFailOnFirstError() {
        // Given
        when(openAiAudioSpeechModel.call(testQuestions.get(0))).thenReturn(mockAudioBytes);
        when(openAiAudioSpeechModel.call(testQuestions.get(1)))
                .thenThrow(new RuntimeException("Network error"));

        // When & Then
        assertThatThrownBy(() -> openAIService.generateTextToAudio(testQuestions))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to generate audio for: " + testQuestions.get(1));
        
        // Verify that it stops at the first failure
        verify(openAiAudioSpeechModel, times(1)).call(testQuestions.get(0));
        verify(openAiAudioSpeechModel, times(1)).call(testQuestions.get(1));
        verify(openAiAudioSpeechModel, never()).call(testQuestions.get(2));
    }

    @Test
    @DisplayName("Should handle case-insensitive HTTP 401 error detection")
    void generateTextToAudio_WithLowercaseHttp401_ShouldThrowAuthenticationException() {
        // Given
        String authErrorMessage = "http 401 - Authentication failed";
        NonTransientAiException authException = new NonTransientAiException(authErrorMessage);
        when(openAiAudioSpeechModel.call(anyString())).thenThrow(authException);

        // When & Then
        assertThatThrownBy(() -> openAIService.generateTextToAudio(testQuestions))
                .isInstanceOf(OpenAiAuthenticationException.class);
    }

    @Test
    @DisplayName("Should handle NonTransientAiException without specific error patterns and return empty list")
    void generateTextToAudio_WithGenericNonTransientAiException_ShouldReturnEmptyList() {
        // Given
        String genericAiErrorMessage = "Unknown AI service error";
        NonTransientAiException genericAiException = new NonTransientAiException(genericAiErrorMessage);
        when(openAiAudioSpeechModel.call(anyString())).thenThrow(genericAiException);

        // When
        List<String> result = openAIService.generateTextToAudio(testQuestions);
        
        // Then
        // The service logs the error but continues processing, resulting in empty list since no audio was generated
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
        verify(openAiAudioSpeechModel, times(3)).call(anyString());
    }

    @Test
    @DisplayName("Should handle questions with special characters and encoding")
    void generateTextToAudio_WithSpecialCharacters_ShouldProcessCorrectly() {
        // Given
        List<String> specialQuestions = Arrays.asList(
                "What's your approach to solving problems with ñoñó characters?",
                "How do you handle UTF-8 encoding: 测试?",
                "Questions with quotes: \"Can you handle this?\""
        );
        when(openAiAudioSpeechModel.call(anyString())).thenReturn(mockAudioBytes);

        // When
        List<String> result = openAIService.generateTextToAudio(specialQuestions);

        // Then
        assertThat(result).hasSize(3);
        assertThat(result).allMatch(audio -> audio.equals(expectedBase64Audio));
        
        specialQuestions.forEach(question -> 
            verify(openAiAudioSpeechModel).call(question)
        );
    }

    @Test
    @DisplayName("Should handle very long questions")
    void generateTextToAudio_WithLongQuestion_ShouldProcessCorrectly() {
        // Given
        String longQuestion = "This is a very long question that might test the limits of the audio generation service. ".repeat(10);
        List<String> longQuestions = Collections.singletonList(longQuestion);
        when(openAiAudioSpeechModel.call(anyString())).thenReturn(mockAudioBytes);

        // When
        List<String> result = openAIService.generateTextToAudio(longQuestions);

        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0)).isEqualTo(expectedBase64Audio);
        verify(openAiAudioSpeechModel).call(longQuestion);
    }

    @Test
    @DisplayName("Should handle large list of questions efficiently")
    void generateTextToAudio_WithManyQuestions_ShouldProcessAllSequentially() {
        // Given
        List<String> manyQuestions = Collections.nCopies(10, "Sample question");
        when(openAiAudioSpeechModel.call(anyString())).thenReturn(mockAudioBytes);

        // When
        List<String> result = openAIService.generateTextToAudio(manyQuestions);

        // Then
        assertThat(result).hasSize(10);
        assertThat(result).allMatch(audio -> audio.equals(expectedBase64Audio));
        verify(openAiAudioSpeechModel, times(10)).call("Sample question");
    }
} 