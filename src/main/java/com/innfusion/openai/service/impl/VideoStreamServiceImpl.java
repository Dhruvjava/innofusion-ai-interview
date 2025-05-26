package com.innfusion.openai.service.impl;

import com.github.kokorin.jaffree.ffmpeg.FFmpeg;
import com.github.kokorin.jaffree.ffmpeg.UrlInput;
import com.github.kokorin.jaffree.ffmpeg.UrlOutput;
import com.innfusion.base.BaseDataRs;
import com.innfusion.openai.constants.MessageCodes;
import com.innfusion.openai.service.VideoStreamService;
import com.innfusion.utils.Messages;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacpp.Loader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@Slf4j
@RequiredArgsConstructor
public class VideoStreamServiceImpl implements VideoStreamService {

    @Value("${innfusion.video.base-dir}")
    private String baseDir;

    @Value("${innfusion.video.tmp-vide-filename}")
    private String tmpVidFilename;

    @Value("${innfusion.video.question-filename}")
    private String queFilename;

    @Value("${innfusion.video.hls-format-dir}")
    private String hlsDir;

    @Value("${innfusion.video.hls-playlist-filename}")
    private String hlsPlayFilename;

    @Value("${innfusion.video.hls-segment-pattern}")
    private String hlsSegPattern;

    private final String ffmpegExecutable;

    private final Messages messages;

    {
        ffmpegExecutable = Loader.load(org.bytedeco.ffmpeg.global.avutil.class, "ffmpeg");
    }

    /**
     * Process the uploaded video and question String: - save question as text, - converts video to
     * hls, - save both in structured directory.
     *
     * @param file          The uploaded vide file
     * @param question      The question String
     * @param intervieweeId The Interviewee Id
     * @param questionId    The Question Id
     * @throws
     */

    @Override
    public BaseDataRs processVideoStream(MultipartFile file, String question, String intervieweeId,
                    String questionId) {
        if (log.isDebugEnabled()) {
            log.debug("Executing processVideoStream(MultipartFile , String, String,String) ->");
        }
        try {
            if (file == null || file.isEmpty()) {
                log.error("File is Empty/Null");
                throw new RuntimeException("File is Empty/Null");
            }
            if (!StringUtils.hasText(question)) {
                log.error("Question is Empty/Null");
                throw new RuntimeException("Question is Empty/Null");
            }
            if (!StringUtils.hasText(intervieweeId)) {
                throw new RuntimeException("Interviewee ID is null or blank.");
            }
            if (!StringUtils.hasText(questionId)) {
                throw new RuntimeException("Question ID is null or blank.");
            }
            final Path intervieweeDir = Paths.get(baseDir, intervieweeId);
            final Path questionDir = intervieweeDir.resolve(questionId);
            // Create Directory if not exists
            Files.createDirectories(questionDir);

            // save Question Text file

            Path questionFile = questionDir.resolve(queFilename);
            Files.writeString(questionFile, question, StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING);

            // Save Video Temporarily
            Path tempVideoPath = questionDir.resolve(tmpVidFilename);
            try (InputStream is = file.getInputStream()) {
                Files.copy(is, tempVideoPath, StandardCopyOption.REPLACE_EXISTING);
            }

            // Create Output Dir for HLS Segment and Playlist
            Path hldOutputDir = questionDir.resolve(hlsDir);

            Files.createDirectories(hldOutputDir);

            // Execute FFmpeg Commant to Convert mp4 to hls
            FFmpeg.atPath(Paths.get(ffmpegExecutable)).addInput(UrlInput.fromPath(tempVideoPath))
                            .addOutput(UrlOutput.toPath(hldOutputDir.resolve(hlsPlayFilename))
                                            .addArguments("-start_number", "0")
                                            .addArguments("-hls_time", "10")
                                            .addArguments("-hls_list_size", "0")
                                            .addArguments("-f", "hls")
                                            .addArguments("-hls_segment_filename",
                                                            hldOutputDir.resolve(hlsSegPattern)
                                                                            .toString())).execute();
            // Delete Temp file after conversation
            Files.deleteIfExists(tempVideoPath);

            String message = messages.getMessage(MessageCodes.MC_UPLOADED_SUCCESSFULLY);
            return new BaseDataRs(message);
        } catch (IOException ioException) {
            log.error("Exception in processVideoStream(MultipartFile , String, String,String) -> {}",
                            ioException.getLocalizedMessage());
            throw new RuntimeException(ioException);
        } catch (Exception e) {
            log.error("Exception in processVideoStream(MultipartFile , String, String,String) -> {}",
                            e.getLocalizedMessage());
            throw e;
        }
    }
}
