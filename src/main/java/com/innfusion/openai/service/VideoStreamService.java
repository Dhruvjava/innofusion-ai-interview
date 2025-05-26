package com.innfusion.openai.service;

import com.innfusion.base.BaseDataRs;
import org.springframework.web.multipart.MultipartFile;

public interface VideoStreamService {

    public BaseDataRs processVideoStream(MultipartFile file, String question, String intervieweeId, String questionId);

}
