package org.codingburgas.demetra.controllers.forms;

import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

@Getter
@Setter
public class EventForm {
    private String title = "";
    private String description = "";
    private String category = "";
    private String location = "";
    private Integer capacity;
    private Double price = 0.0;
    private String startsAt = "";
    private String advertisingText = "";
    private String advertisingImageUrl = "";
    private String videoPath = "";
    private String videoFileName = "";
    private String videoContentType = "";
    private MultipartFile video = null;
}
