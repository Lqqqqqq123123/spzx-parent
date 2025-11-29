package com.spzx.report.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.spzx.report.domain.VOrderInfo;

import java.util.List;
import java.util.Map;

public interface ISpzxReportService extends IService<VOrderInfo> {

    /**
     * 数据库直接返回 count 以及 groupTag，
     * @param question
     * @return
     */
    List<Map<String, Object>> getAiReport(String question);
}
