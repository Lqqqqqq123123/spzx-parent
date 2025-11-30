<template>
  <div>
    <div class="search-div">
      <el-form label-width="90px" size="small">
        <el-row>
          <el-col :span="22">
            <el-form-item label="输入搜索信息">
              <el-input v-model="keywordText" />
            </el-form-item>
          </el-col>
          <el-col :span="1"></el-col>
          <el-col :span="1">
            <el-button type="primary" size="small" @click="fetchData()">
              搜索
            </el-button>
          </el-col>
        </el-row>
      </el-form>
    </div>
    <div ref="chart" style="width: 100%; height: 500px"></div>
  </div>
</template>
<script setup>
import * as echarts from "echarts";
import { onMounted, ref } from "vue";
import { GetOrderStatisticsData } from "@/api/order/orderInfo.js";

onMounted(async () => {
  fetchData();
});

const chart = ref();
const keywordText = ref("目前最畅销商品排行");
const fetchData = async () => {
  const { data } = await GetOrderStatisticsData(keywordText.value);
  console.log(data);
  setChartOption(data.xList, data.yList);
};

const setChartOption = (xList, yList) => {
  const myChart = echarts.init(chart.value);
  // 指定图表的配置项和数据
  const option = {
    title: {
      text: "订单动态统计",
    },
    tooltip: {},
    legend: {
      data: ["订单相关信息总量"],
    },
    xAxis: {
      data: xList,
    },
    yAxis: {},
    series: [
      {
        name: "订单总金额（元）",
        type: "bar",
        data: yList,
      },
    ],
  };
  // 使用刚指定的配置项和数据显示图表。
  myChart.setOption(option);
};
</script>
<style scoped>
.search-div {
  margin-bottom: 10px;
  padding: 10px;
  border: 1px solid #ebeef5;
  border-radius: 3px;
  background-color: #fff;
}
</style>