package com.GooglePay.Billing;
import java.util.List;

public interface GoogleBillingResultCallback  {
    public void ResultCallback(String result);

    public void Log(String log);

    public void queryPurchasesSuccess(List<String> productTokens);

    public void queryPurchasesFail(String billingResult);

    public void queryProductDetailsSuccess(List<String> productIds, List<String> price, List<String> currencyCodes);

    public void queryProductDetailsFail(String billingResult);

    public void productBuySuccess(String orderId, String token);

    public void productBuyFail(String billingResult);

    public void consumePurchaseSuccess(String token);

    public void consumePurchaseFail(String billingResult);
}