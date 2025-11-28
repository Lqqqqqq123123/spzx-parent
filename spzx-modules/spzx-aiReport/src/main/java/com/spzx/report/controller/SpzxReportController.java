package com.spzx.report.controller;

import com.spzx.common.core.web.domain.AjaxResult;
import com.spzx.report.domain.VOrderInfo;
import com.spzx.report.service.ISpzxReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.spzx.common.core.web.domain.AjaxResult.success;

@RestController
@RequestMapping
public class SpzxReportController {

    @Autowired
    private ISpzxReportService spzxReportService;

    @GetMapping("/getAllReport")
    public AjaxResult getAllReport(){
        List<VOrderInfo> list = spzxReportService.list();
        return success(list);
    }
}
