package org.codingburgas.demetra.controllers;

import org.codingburgas.demetra.services.DataQualityService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminDataQualityController {
    private final DataQualityService dataQualityService;

    public AdminDataQualityController(DataQualityService dataQualityService) {
        this.dataQualityService = dataQualityService;
    }

    @GetMapping("/admin/data-quality")
    public String dataQuality(Model model) {
        model.addAttribute("report", dataQualityService.inspect());
        return "admin/data-quality";
    }
}
