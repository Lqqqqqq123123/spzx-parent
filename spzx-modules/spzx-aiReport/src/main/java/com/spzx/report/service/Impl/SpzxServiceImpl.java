package com.spzx.report.service.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.spzx.report.domain.VOrderInfo;
import com.spzx.report.mapper.VOrderInfoMapper;
import com.spzx.report.service.ISpzxReportService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class SpzxServiceImpl extends ServiceImpl<VOrderInfoMapper, VOrderInfo> implements ISpzxReportService{

    @Override
    public List<Map<String, Object>> getAiReport(String question) {
        return List.of();
    }
}
