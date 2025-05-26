package com.innfusion.utils;

import jakarta.annotation.PostConstruct;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class Messages {

    private final MessageSource messageSource;

    private MessageSourceAccessor messageSourceAccessor;

    @PostConstruct
    public void init(){
        messageSourceAccessor = new MessageSourceAccessor(messageSource, Locale.ENGLISH);
    }

    public String getMessage(String code){
        return messageSourceAccessor.getMessage(code);
    }

}
