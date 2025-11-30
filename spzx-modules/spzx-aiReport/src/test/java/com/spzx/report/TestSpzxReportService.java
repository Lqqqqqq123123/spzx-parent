package com.spzx.report;

import com.spzx.report.service.ISpzxReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class TestSpzxReportService {

    @Autowired
    private ISpzxReportService spzxReportService;

    @Test
    public void test01(){
        spzxReportService.getAiReport("最热门的商品");
    }
}
