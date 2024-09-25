package com.GooglePay.Billing;

import android.app.Activity;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ConsumeParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesResponseListener;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

public class GoogleBillingManager{

    public BillingClient mBillingclient;
    private Activity mActivity;
    private GoogleBillingResultCallback mResultCallback;
    private HashMap<ConnectionListenerType, BillingConnectionListener> mConnectionListenerMap = new HashMap<>();;

    public enum ConnectionListenerType
    {
        QueryPurchase,
        QueryProduct,
        Pay,
        Consume
    }

    // 接口定义
    public interface BillingConnectionListener {
        void onBillingSetupFinished(boolean success);
    }

    public GoogleBillingManager(Activity activity)
    {
        mActivity = activity;
    }

    /**
     * 初始化billing
     */
    public void init(GoogleBillingResultCallback resultCallback)
    {
        Log.i("TAG", "初始化发起");

        PurchasesUpdatedListener mPurchasesUpdatedListener = new PurchasesUpdatedListener() {
            @Override
            public void onPurchasesUpdated(@NonNull BillingResult billingResult, @Nullable List<Purchase> list) {
                String debugMessage = billingResult.getDebugMessage();
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    mResultCallback.Log("支付成功");
                    if (list != null && !list.isEmpty()) {
                        for (Purchase purchase : list) {
                            if (purchase == null || purchase.getPurchaseState() != Purchase.PurchaseState.PURCHASED)
                                continue;

                            //客户端同步回调支付成功
                            mResultCallback.productBuySuccess(purchase.getOrderId(), purchase.getPurchaseToken());
                        }
                    }
                }
                 else {
                     mResultCallback.Log("支付失败");
                     mResultCallback.productBuyFail(billingResult.getDebugMessage());
                     mResultCallback.ResultCallback(billingResult.getDebugMessage());
                }
            }
        };

        mResultCallback = resultCallback;
        mBillingclient = BillingClient.newBuilder(mActivity).setListener(mPurchasesUpdatedListener).enablePendingPurchases().build();
    }

    /**
     * 开始连接服务器
     */
    public void startConnect(ConnectionListenerType connectionListenerType, BillingConnectionListener connectionListener)
    {
        mConnectionListenerMap.put(connectionListenerType, connectionListener);
        Log.i("TAG", "连接发起");
        mBillingclient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingServiceDisconnected() {
                // 连接失败
                Log.i("TAG", "连接失败");
                mConnectionListenerMap.get(connectionListenerType).onBillingSetupFinished(false);
            }

            @Override
            public void onBillingSetupFinished(@NonNull BillingResult billingResult) {
                // 连接成功
                Log.i("TAG", "连接成功");
                mConnectionListenerMap.get(connectionListenerType).onBillingSetupFinished(true);
            }
        });
    }

    public void CheckConnect(ConnectionListenerType listenerType, BillingConnectionListener connectionListener)
    {
        if(mBillingclient.getConnectionState() == BillingClient.ConnectionState.CONNECTED)
        {
            // 正常查询逻辑
            Log.i("TAG", "检查连接成功");
            connectionListener.onBillingSetupFinished(true);
        }
        else
        {
            // 重新连接逻辑 等到连接完毕 执行查询
            Log.i("TAG", "检查连接失败");
            startConnect(listenerType, success -> {
                Log.i("TAG", "连接成功回调");
                if(success)
                {
                    // 连接成功后继续执行逻辑
                    connectionListener.onBillingSetupFinished(true);
                }
            });
        }
    }

    /**
    * 查询商品详情
     */
    public void queryProductDetails(List<String> productIds)
    {
        Log.i("TAG", "查询商品");
        CheckConnect(ConnectionListenerType.QueryProduct, success ->
        {
            if(success)
            {
                // 连接成功
                onQueryProductDetails(productIds, (productDetailsList)->
                {
                    // 获取成功
                    List<String> productIdResultList = new ArrayList<>();
                    List<String> prices = new ArrayList<>();
                    List<String> currencyCodes = new ArrayList<>();
                    for (ProductDetails productDetail : productDetailsList)
                    {
                        // 获取价格信息
                        String productId = productDetail.getProductId();
                        String price = productDetail.getOneTimePurchaseOfferDetails().getFormattedPrice();
                        String currencyCode = productDetail.getOneTimePurchaseOfferDetails().getPriceCurrencyCode();
                        //long priceAmountMicros = productDetails.getOneTimePurchaseOfferDetails().getPriceAmountMicros();

                        productIdResultList.add(productId);
                        prices.add(price);
                        currencyCodes.add(currencyCode);
                    }
                    // 查询成功
                    mResultCallback.queryProductDetailsSuccess(productIdResultList, prices, currencyCodes);
                }, (billingResult)->
                {
                    mResultCallback.queryProductDetailsFail(billingResult.getDebugMessage());
                });
            }
        });
    }

    private void onQueryProductDetails(final List<String> productIds, Consumer<List<ProductDetails>> successConsumer, Consumer<BillingResult> failConsumer)
    {
        // 将商品全部转换为Product类
        List<QueryProductDetailsParams.Product> paramsList = new ArrayList<>();
        for (String productId : productIds)
        {
            QueryProductDetailsParams.Product queryProductDetailsParams = QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(productId)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build();

            paramsList.add(queryProductDetailsParams);
        }

        QueryProductDetailsParams queryProductDetailsParams = QueryProductDetailsParams.newBuilder()
                .setProductList(paramsList)
                .build();

        mBillingclient.queryProductDetailsAsync(queryProductDetailsParams,
                (billingResult, list) ->
                {
                    // 商品查询回调
                    // 检测 billingResult
                    if(billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK)
                    {
                        // 成功 则返回商品列表
                        successConsumer.accept(list);
                    }
                    else
                    {
                        failConsumer.accept(billingResult);
                    }
                }
        );
    }
    /**
     * 支付
     */
    public void pay(String productId)
    {
        CheckConnect(ConnectionListenerType.Pay, success ->
        {
            if(success)
            {
                onPay(productId);
            }
        });
    }

    private void onPay(final String productId)
    {
        mResultCallback.Log("进入支付");
        // 此处需要先查询商品 然后再去拉起
        ArrayList<String> tempList = new ArrayList<String>();
        tempList.add(productId);
        onQueryProductDetails(tempList,(productDetailsList)->
        {
            ProductDetails details = productDetailsList.get(0);

            mResultCallback.Log("支付的商品" + details.getProductId());
            ImmutableList<BillingFlowParams.ProductDetailsParams> productDetailsParamsList =
                    ImmutableList.of(
                            BillingFlowParams.ProductDetailsParams.newBuilder()
                                    // retrieve a value for "productDetails" by calling queryProductDetailsAsync()
                                    .setProductDetails(details)
                                    // For one-time products, "setOfferToken" method shouldn't be called.
                                    // For subscriptions, to get an offer token, call
                                    // ProductDetails.subscriptionOfferDetails() for a list of offers
                                    // that are available to the user.
                                    //.setOfferToken(selectedOfferToken)
                                    .build()
                    );

            BillingFlowParams billingFlowParams = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(productDetailsParamsList)
                    .build();
            // 客户端拉起支付
            BillingResult billingResult = mBillingclient.launchBillingFlow(mActivity, billingFlowParams);
            mResultCallback.Log("拉起回调" + billingResult.getDebugMessage());
        },(billingResult)->
        {
            mResultCallback.Log("查询失败");
            mResultCallback.ResultCallback(billingResult.getDebugMessage());
        });
    }

    /**
     * 消耗商品
     */
    public void consumePurchase(final String purchaseToken)
    {
        CheckConnect(ConnectionListenerType.Consume, success ->
        {
            if(success)
            {
                onConsumePurchase(purchaseToken);
            }
        });
    }

    private void onConsumePurchase(final String purchaseToken)
    {
        ConsumeParams consumeParams = ConsumeParams.newBuilder()
                .setPurchaseToken(purchaseToken)
                .build();
        ConsumeResponseListener listener = new ConsumeResponseListener() {
            @Override
            public void onConsumeResponse(BillingResult billingResult, String purchaseToken) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.ERROR) {
                    //消费失败将商品重新放入消费队列
                    mResultCallback.Log("消费失败");
                    mResultCallback.consumePurchaseFail(billingResult.getDebugMessage());
                    return;
                }

                mResultCallback.Log("消费成功");
                mResultCallback.consumePurchaseSuccess(purchaseToken);
            }
        };
        mBillingclient.consumeAsync(consumeParams, listener);
    }

    /**
     * 补单操作 查询已支付的商品，并通知服务器后消费（google的支付里面，没有消费的商品，不能再次购买）
     */
     public void queryPurchases()
     {
         CheckConnect(ConnectionListenerType.QueryPurchase, success ->
         {
             if(success)
             {
                 onQueryPurchases();
             }
         });
     }

    private void onQueryPurchases()
    {
        // 新建一个回调方法 专门用于处理补单的商品
        PurchasesResponseListener mPurchasesResponseListener = new PurchasesResponseListener() {
            @Override
            public void onQueryPurchasesResponse(@NonNull BillingResult billingResult, @NonNull List<Purchase> purchasesResult) {
                if(billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK)
                {
                    List<String> purchaseTokens = new ArrayList<>();
                    if(purchasesResult != null)
                    {
                        for (Purchase purchase : purchasesResult) {
                            if(purchase == null || purchase.getPurchaseState() != Purchase.PurchaseState.PURCHASED) continue;

                            //这里处理已经支付过的订单，通知服务器去验证  并消耗商品
                            purchaseTokens.add(purchase.getPurchaseToken());
                            mResultCallback.Log("唤起了补单操作 这里处理已经支付过的订单，通知服务器去验证  并消耗商品");
                        }
                    }
                    mResultCallback.queryPurchasesSuccess(purchaseTokens);
                }
                else
                {
                    mResultCallback.queryPurchasesFail(billingResult.getDebugMessage());
                }
            }
        };

        QueryPurchasesParams purchasesParams = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build();

        mBillingclient.queryPurchasesAsync(purchasesParams, mPurchasesResponseListener);
    }
}