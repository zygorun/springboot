package com.gorun.demo.base;

public class BaseController {
    protected <T> HttpResultModel<T> sendResult(T result, Integer total) {
        HttpResultModel<T> resultModel = new HttpResultModel<>();
        resultModel.setSendTime(System.currentTimeMillis());
        resultModel.setTotal(total);
        resultModel.setData(result);

        return resultModel;
    }
}
