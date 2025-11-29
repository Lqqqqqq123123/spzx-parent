package com.spzx.report.controller;

import com.spzx.common.core.web.controller.BaseController;
import com.spzx.common.core.web.domain.AjaxResult;
import com.spzx.report.domain.VOrderInfo;
import com.spzx.report.service.ISpzxReportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.spzx.common.core.web.domain.AjaxResult.success;

@RestController
@RequestMapping
public class SpzxReportController extends BaseController {

    @Autowired
    ISpzxReportService spzxReportService;

    @GetMapping("getAiReport/{question}")
    public AjaxResult getAiReport(@PathVariable String question) {
        Map<String, Object> xyMap = new HashMap<>();
        List<Object> xList = new ArrayList<>();
        List<Object> yList = new ArrayList<>();


        List<Map<String, Object>> reportMaps = spzxReportService.getAiReport(question);
        for (Map<String, Object> reportMap : reportMaps) {
            String groups = reportMap.get("groupTag").toString();
            Object count = reportMap.get("count");
            xList.add(groups);
            yList.add(count);
        }
        xyMap.put("xList", xList);
        xyMap.put("yList", yList);
        return success(xyMap);
    }

}
