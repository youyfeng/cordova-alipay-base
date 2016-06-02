# cordova-alipay-base 

Cordova 支付宝基础功能插件

# 功能

目前只有支付功能

# 安装

1. 运行

```
cordova plugin add https://github.com/pipitang/cordova-alipay-base --variable ALI_PID=yourpid

```

2. cordova各种衍生命令行都应该支持，例如phonegap或者ionic。

# 使用方法

## 注意

阿里官方的例子只是演示了支付参数的调用，在实际项目中决不可使用。在客户端使用appkey，更别提private_key了，危险隐患重重。

安全的使用方式应该是由服务端保存key，然后根据客户端传来的订单id，装载订单内容，生成支付字符串，最后由客户端提交给支付网关。另外，服务端返回支付字符串时，除了签名顺序一致以外，合成的key=value是要带双引号的，这点比较坑爹，害了我调试半天, 想见后面Java代码:

```
public static String createLinkString(Map<String, String> params, boolean client) {
	List<String> keys = new ArrayList<String>(params.keySet());
	Collections.sort(keys);

	StringBuffer buf = new StringBuffer();

	for (int i = 0; i < keys.size(); i++) {
		String key = keys.get(i);
		String value = params.get(key);
		buf.append(key).append('=');
		if (client) buf.append('"');
		buf.append(value); 
		if (client) buf.append('"');
		buf.append('&');
	}

	buf.deleteCharAt(buf.length()-1);
	return buf.toString();
}

```

我们的项目中为了兼容微信支付，生成checkout返回的信息为json对象，其实本质上字符串即可。。


## API

### 支付API


```
    Alipay.Base.pay(parameters, success, failure); 

```

此处第一个参数为json对象，请从服务端获取，直接传给改方法。客户端会对服务端返回的JSON对象属性进行排序，js层不需要关心。具体服务端参数合成，java代码请参照一下内容及阿里官方文档，注意createLinkString上得注释：

在项目中客户端使用如下：
```
orderService.checkout(orderId, $scope.selectPay).then(function (parameters) {
    if ('Wechat' === $scope.selectPay) callNativeWexinPayment(parameters); {
    else Alipay.Base.pay(parameters, function(result){
        if(result.resultStatus==='9000'||result.resultStatus==='8000') finishPayment();
        else showPaymentError(null);
    }, showPaymentError);
}

```

服务端如下，可以把Map直接作为JSON返回：

```
private static Map<String, String> checkoutAlipay(AlipayConfig config, String orderNumber, BigDecimal amount) throws Exception{
    Map<String, String> orderInfo=new HashMap<String,String>();
    orderInfo.put("service", "mobile.securitypay.pay");
    orderInfo.put("partner", aliConfig.getPartnerId());
    orderInfo.put("_input_charset", "utf-8");
    orderInfo.put("notify_url", aliConfig.getPaymentConfirm());
    orderInfo.put("out_trade_no", orderNumber);

    orderInfo.put("total_fee", amount + "");
    orderInfo.put("subject", "XXXXXX收费");

    orderInfo.put("payment_type", "1");
    orderInfo.put("seller_id", aliConfig.getSellerId());
    orderInfo.put("body", "xxxx yykskdfksdf 服务费");

    // 设置未付款交易的超时时间
    // 默认30分钟，一旦超时，该笔交易就会自动被关闭。
    // 取值范围：1m～15d。
    // m-分钟，h-小时，d-天，1c-当天（无论交易何时创建，都在0点关闭）。
    // 该参数数值不接受小数点，如1.5h，可转换为90m。
    orderInfo.put("it_b_pay", "7d");
    // extern_token为经过快登授权获取到的alipay_open_id,带上此参数用户将使用授权的账户进行支付
    // orderInfo += "&extern_token=" + "\"" + extern_token + "\"";
    // We have to tell the client
    orderInfo.put("sign", URLEncoder.encode(AlipayUtil.sign(orderInfo, aliConfig.getPrivateKey()), "utf-8"));
    orderInfo.put("sign_type", "RSA");
    return orderInfo;
}

```

```
public class AlipayConfig {

    private String partnerId;
    private String paymentConfirm;
    private String sellerId;
    private String privateKey;
    private String publicKey;

    public String getPartnerId() {
        return partnerId;
    }

    public void setPartnerId(String partnerId) {
        this.partnerId = partnerId;
    }

    public String getPaymentConfirm() {
        return paymentConfirm;
    }

    public void setPaymentConfirm(String paymentConfirm) {
        this.paymentConfirm = paymentConfirm;
    }

    public String getSellerId() {
        return sellerId;
    }

    public void setSellerId(String sellerId) {
        this.sellerId = sellerId;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }
}

```

```

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;

import org.springframework.util.Base64Utils;

public abstract class AlipayUtil {
    public static final String SIGN_ALGORITHMS = "SHA1WithRSA";
    /** 支付宝消息验证地址 */
    private static final String HTTPS_VERIFY_URL = "https://mapi.alipay.com/gateway.do?service=notify_verify&";

    public static String sign(Map<String, String> content, String privateKey) throws Exception {
        return sign(createLinkString(content, true), privateKey, "utf-8");
    }

    /** RSA签名
    * @param content 待签名数据
    * @param privateKey 商户私钥
    * @param input_charset 编码格式
    * @return 签名值
    * @throws Exception */
    private static String sign(String content, String privateKey, String input_charset) throws Exception {
        PKCS8EncodedKeySpec priPKCS8 = new PKCS8EncodedKeySpec(Base64Utils.decodeFromString(privateKey));
        KeyFactory keyf = KeyFactory.getInstance("RSA");
        PrivateKey priKey = keyf.generatePrivate(priPKCS8);
        java.security.Signature signature = java.security.Signature.getInstance(SIGN_ALGORITHMS);
        signature.initSign(priKey);
        signature.update(content.getBytes(input_charset));
        byte[] signed = signature.sign();
        return Base64Utils.encodeToString(signed);
    }

    /** RSA验签名检查
    * @param content 待签名数据
    * @param sign 签名值
    * @param ali_public_key 支付宝公钥
    * @param input_charset 编码格式
    * @return 布尔值 */
    private static boolean verify(String content, String sign, String ali_public_key, String input_charset) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            byte[] encodedKey = Base64Utils.decodeFromString(ali_public_key);
            PublicKey pubKey = keyFactory.generatePublic(new X509EncodedKeySpec(encodedKey));
            java.security.Signature signature = java.security.Signature.getInstance(SIGN_ALGORITHMS);
            signature.initVerify(pubKey);
            signature.update(content.getBytes(input_charset));
            return signature.verify(Base64Utils.decodeFromString(sign));
        } catch (Exception e) { e.printStackTrace();}

        return false;
    }

    /** 解密
    * @param content 密文
    * @param private_key 商户私钥
    * @param input_charset 编码格式
    * @return 解密后的字符串 */
    public static String decrypt(String content, String private_key, String input_charset) throws Exception {
        PrivateKey prikey = getPrivateKey(private_key);
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, prikey);
        InputStream ins = new ByteArrayInputStream(Base64Utils.decodeFromString(content));
        ByteArrayOutputStream writer = new ByteArrayOutputStream();
        // rsa解密的字节大小最多是128，将需要解密的内容，按128位拆开解密
        byte[] buf = new byte[128];
        int bufl;

        while ((bufl = ins.read(buf)) != -1) {
            byte[] block = null;

            if (buf.length == bufl) {
                block = buf;
            } else {
                block = new byte[bufl];
                for (int i = 0; i < bufl; i++) {
                    block[i] = buf[i];
                }
            }
            writer.write(cipher.doFinal(block));
        }

        return new String(writer.toByteArray(), input_charset);
    }

    /** 得到私钥
    * @param key 密钥字符串（经过base64编码）
    * @throws Exception */
    public static PrivateKey getPrivateKey(String key) throws Exception {
        byte[] keyBytes = Base64Utils.decodeFromString(key);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PrivateKey privateKey = keyFactory.generatePrivate(keySpec);
        return privateKey;
    }

    /** 除去数组中的空值和签名参数
    * @param sArray 签名参数组
    * @return 去掉空值与签名参数后的新签名参数组 */
    public static Map<String, String> paraFilter(Map<String, String> sArray) {

        Map<String, String> result = new HashMap<String, String>();

        if (sArray == null || sArray.size() <= 0) { return result; }

        for (String key : sArray.keySet()) {
        String value = sArray.get(key);
        if (value == null || value.equals("") || key.equalsIgnoreCase("sign") || key.equalsIgnoreCase("sign_type")){
            continue;
            }
            result.put(key, value);
        }

        return result;
    }

    /** 把数组所有元素排序，并按照“参数=参数值”的模式用“&”字符拼接成字符串
    * @param params 需要排序并参与字符拼接的参数组
    * @param client 是否用于给客户端合成签名使用，AliPay的设计实在让人郁闷，提交的数据需要双引号把内容括起来，但是向我们服务器通知的数据居然没有引号。
    * @return 拼接后字符串 */
    public static String createLinkString(Map<String, String> params, boolean client) {
        List<String> keys = new ArrayList<String>(params.keySet());
        Collections.sort(keys);

        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            String value = params.get(key);
            buf.append(key).append('=');
            if (client) buf.append('"');
            buf.append(value); 
            if (client) buf.append('"');
            buf.append('&');
        }

        buf.deleteCharAt(buf.length()-1);
        return buf.toString();
    }


    /** 验证消息是否是支付宝发出的合法消息
    * @param params 通知返回来的参数数组
    * @return 验证结果 */
    public static boolean verify(Map<String, String> params, String partner, String publicKey) {

        // 判断responsetTxt是否为true，isSign是否为true
        // responsetTxt的结果不是true，与服务器设置问题、合作身份者ID、notify_id一分钟失效有关
        // isSign不是true，与安全校验码、请求时的参数格式（如：带自定义参数等）、编码格式有关
        String responseTxt = "false";
        if (params.get("notify_id") != null) {
            String notify_id = params.get("notify_id");
            responseTxt = verifyResponse(notify_id, partner);
        }
        String sign = "";
        if (params.get("sign") != null) {
            sign = params.get("sign");
        }
        boolean isSign = getSignVeryfy(params, sign, publicKey);
        return isSign && responseTxt.equals("true");
    }

    /** 根据反馈回来的信息，生成签名结果
    * @param Params 通知返回来的参数数组
    * @param sign 比对的签名结果
    * @return 生成的签名结果 */
    private static boolean getSignVeryfy(Map<String, String> Params, String sign, String publicKey) {
        // 过滤空值、sign与sign_type参数, 获取待签名字符串
        String preSignStr = createLinkString(paraFilter(Params), false);
        // 获得签名验证结果
        return verify(preSignStr, sign, publicKey, "utf-8");
    }

    /** 获取远程服务器ATN结果,验证返回URL
    * @param notify_id 通知校验ID
    * @return 服务器ATN结果 验证结果集： invalid命令参数不对 出现这个错误，请检测返回处理中partner和key是否为空 true 返回正确信息 false 请检查防火墙或者是服务器阻止端口问题以及验证时间是否超过一分钟 */
    private static String verifyResponse(String notify_id, String partner) {
        // 获取远程服务器ATN结果，验证是否是支付宝服务器发来的请求
        String veryfy_url = HTTPS_VERIFY_URL + "partner=" + partner + "&notify_id=" + notify_id;
        return checkUrl(veryfy_url);
    }

    /** 获取远程服务器ATN结果
    * @param urlvalue 指定URL路径地址
    * @return 服务器ATN结果 验证结果集： invalid命令参数不对 出现这个错误，请检测返回处理中partner和key是否为空 true 返回正确信息 false 请检查防火墙或者是服务器阻止端口问题以及验证时间是否超过一分钟 */
    private static String checkUrl(String urlvalue) {
        String inputLine = "";

        try {
            URL url = new URL(urlvalue);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
            inputLine = in.readLine().toString();
        } catch (Exception e) {
            e.printStackTrace();
            inputLine = "";
        }

        return inputLine;
    }
}

```

# 现有项目用例
```
package com.xc.freeapp.util.alipay;

import com.xc.freeapp.config.AlipayConfig;
import com.xc.freeapp.sign.RSA;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;

import static com.xc.freeapp.config.AlipayConfig.sign_type;

/* *
 *类名：AlipayNotify
 *功能：支付宝通知处理类
 *详细：处理支付宝各接口通知返回
 *版本：3.3
 *日期：2012-08-17
 *说明：
 *以下代码只是为了方便商户测试而提供的样例代码，商户可以根据自己网站的需要，按照技术文档编写,并非一定要使用该代码。
 *该代码仅供学习和研究支付宝接口使用，只是提供一个参考

 *************************注意*************************
 *调试通知返回时，可查看或改写log日志的写入TXT里的数据，来检查通知返回是否正常
 */
public class AlipayNotify {

  /**
   * 支付宝消息验证地址
   */
  private static final String HTTPS_VERIFY_URL = "https://mapi.alipay.com/gateway.do?service=notify_verify&";

  /**
   * 验证消息是否是支付宝发出的合法消息
   *
   * @param params 通知返回来的参数数组
   * @return 验证结果
   */
  public static boolean verify(Map<String, String> params) {

    //判断responsetTxt是否为true，isSign是否为true
    //responsetTxt的结果不是true，与服务器设置问题、合作身份者ID、notify_id一分钟失效有关
    //isSign不是true，与安全校验码、请求时的参数格式（如：带自定义参数等）、编码格式有关
    String responseTxt = "false";
    if (params.get("notify_id") != null) {
      String notify_id = params.get("notify_id");
      responseTxt = verifyResponse(notify_id);
    }
    String sign = "";
    if (params.get("sign") != null) {
      sign = params.get("sign");
    }
    boolean isSign = getSignVeryfy(params, sign);

    //写日志记录（若要调试，请取消下面两行注释）
    //String sWord = "responseTxt=" + responseTxt + "\n isSign=" + isSign + "\n 返回回来的参数：" + AlipayCore.createLinkString(params);
    //AlipayCore.logResult(sWord);

    if (isSign && responseTxt.equals("true")) {
      return true;
    } else {
      return false;
    }
  }

  /**
   * 根据反馈回来的信息，生成签名结果
   *
   * @param Params 通知返回来的参数数组
   * @param sign   比对的签名结果
   * @return 生成的签名结果
   */
  private static boolean getSignVeryfy(Map<String, String> Params, String sign) {
    //过滤空值、sign与sign_type参数
    Map<String, String> sParaNew = AlipayCore.paraFilter(Params);
    //获取待签名字符串
    String preSignStr = AlipayCore.createLinkString(sParaNew);
    //获得签名验证结果
    boolean isSign = false;
    if (sign_type.equals("RSA")) {
      isSign = RSA.verify(preSignStr, sign, AlipayConfig.ali_public_key, AlipayConfig.input_charset);
    }
    return isSign;
  }



  /**
   * 追加签名sign
   */
  public static String addSign(Map<String, String> Params) {
    //过滤空值、sign与sign_type参数

    Map<String, String> sParaNew = AlipayCore.paraFilter(Params);
    //获取待签名字符串
//    String preSignStr = AlipayCore.createLinkString(sParaNew);
    String preSignStr = createRequestParameters(sParaNew);
    String sign = RSA.sign(preSignStr, AlipayConfig.private_key, AlipayConfig.input_charset);
    try {
      sign= URLEncoder.encode(sign, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    return preSignStr + "&sign=\"" + sign + "\"&sign_type=\"" + AlipayConfig.sign_type + "\"";

  }

  public static String createRequestParameters(Map<String, String> Params) {
    // 签约合作者身份ID
    String orderInfo = "partner=" + "\"" + AlipayConfig.partner + "\"";

    // 签约卖家支付宝账号
    orderInfo += "&seller_id=" + "\"" + AlipayConfig.seller_id + "\"";

    // 商户网站唯一订单号
    orderInfo += "&out_trade_no=" + "\"" + Params.get("out_trade_no") + "\"";

    // 商品名称
    orderInfo += "&subject=" + "\"" + Params.get("subject") + "\"";

    // 商品详情
    orderInfo += "&body=" + "\"" + Params.get("body") + "\"";

    // 商品金额
    orderInfo += "&total_fee=" + "\"" + Params.get("total_fee") + "\"";

    // 服务器异步通知页面路径
    orderInfo += "&notify_url=" + "\"" + AlipayConfig.notify_url
                 + "\"";

    // 服务接口名称， 固定值
    orderInfo += "&service=\"" + AlipayConfig.service
                 + "\"";

    // 支付类型， 固定值
    orderInfo += "&payment_type=\"1\"";

    // 参数编码， 固定值
    orderInfo += "&_input_charset=\"" + AlipayConfig.input_charset
                 + "\"";

    // 设置未付款交易的超时时间
    // 默认30分钟，一旦超时，该笔交易就会自动被关闭。
    // 取值范围：1m～15d。
    // m-分钟，h-小时，d-天，1c-当天（无论交易何时创建，都在0点关闭）。
    // 该参数数值不接受小数点，如1.5h，可转换为90m。
    orderInfo += "&it_b_pay=\"30m\"";

    // extern_token为经过快登授权获取到的alipay_open_id,带上此参数用户将使用授权的账户进行支付
    // orderInfo += "&extern_token=" + "\"" + extern_token + "\"";

    // 支付宝处理完请求后，当前页面跳转到商户指定页面的路径，可空
    orderInfo += "&return_url=\"m.alipay.com\"";

    // 调用银行卡支付，需配置此参数，参与签名， 固定值 （需要签约《无线银行卡快捷支付》才能使用）
    // orderInfo += "&paymethod=\"expressGateway\"";
    return orderInfo;
  }


  /**
   * 获取远程服务器ATN结果,验证返回URL
   *
   * @param notify_id 通知校验ID
   * @return 服务器ATN结果 验证结果集： invalid命令参数不对 出现这个错误，请检测返回处理中partner和key是否为空 true 返回正确信息 false
   * 请检查防火墙或者是服务器阻止端口问题以及验证时间是否超过一分钟
   */
  private static String verifyResponse(String notify_id) {
    //获取远程服务器ATN结果，验证是否是支付宝服务器发来的请求

    String partner = AlipayConfig.partner;
    String veryfy_url = HTTPS_VERIFY_URL + "partner=" + partner + "&notify_id=" + notify_id;

    return checkUrl(veryfy_url);
  }

  /**
   * 获取远程服务器ATN结果
   *
   * @param urlvalue 指定URL路径地址
   * @return 服务器ATN结果 验证结果集： invalid命令参数不对 出现这个错误，请检测返回处理中partner和key是否为空 true 返回正确信息 false
   * 请检查防火墙或者是服务器阻止端口问题以及验证时间是否超过一分钟
   */
  private static String checkUrl(String urlvalue) {
    String inputLine = "";

    try {
      URL url = new URL(urlvalue);
      HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
      BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection
                                                                       .getInputStream()));
      inputLine = in.readLine().toString();
    } catch (Exception e) {
      e.printStackTrace();
      inputLine = "";
    }

    return inputLine;
  }
}

```

```
package com.xc.freeapp.controller;

import com.alibaba.fastjson.JSONObject;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.xc.freeapp.bean.AuthInfo;
import com.xc.freeapp.common.OrderState;
import com.xc.freeapp.common.OrderTradeStatus;
import com.xc.freeapp.entity.TOrder;
import com.xc.freeapp.exception.BaseException;
import com.xc.freeapp.filter.TokenAnnotation;
import com.xc.freeapp.request.OrderRequest;
import com.xc.freeapp.service.AliPayService;
import com.xc.freeapp.service.OrderService;
import com.xc.freeapp.util.AuthUtil;
import com.xc.freeapp.util.DateUtils;
import com.xc.freeapp.util.alipay.AlipayNotify;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by guoshuaipeng on 2016/5/6.
 */
@Controller
@RequestMapping("/alipay")
@Api(value = "/alipay", description = "支付宝支付")
@Slf4j
public class AlipayController extends AbstractController {


    @Autowired
    private OrderService orderService;

    @Autowired
    private AliPayService aliPayService;


    private String SUCCESS = "success";
    private String FAIL = "fail";

    @TokenAnnotation(required = false)
    @RequestMapping(value = "/callback", method = RequestMethod.POST)
    @ApiOperation(value = "支付回调接口")
    @ResponseBody
    public String callback(HttpServletRequest request,
                           HttpServletResponse response) throws Exception {
        try {

            //获取支付宝POST过来反馈信息
            Map<String, String> params = new HashMap<String, String>();
            Map requestParams = request.getParameterMap();
            Iterator iter = requestParams.keySet().iterator();
            while (iter.hasNext()) {
                String name = (String) iter.next();
                String[] values = (String[]) requestParams.get(name);
                String valueStr = "";
                for (int i = 0; i < values.length; i++) {
                    valueStr = (i == values.length - 1) ? valueStr + values[i]
                            : valueStr + values[i] + ",";
                }

                //乱码解决，这段代码在出现乱码时使用。如果mysign和sign不相等也可以使用这段代码转化
                //valueStr = new String(valueStr.getBytes("ISO-8859-1"), "gbk");
                params.put(name, valueStr);
            }
            log.info("支付回调接口:" + JSONObject.toJSONString(params));

            String trade_status = new String(request.getParameter("trade_status").getBytes("ISO-8859-1"), "UTF-8");
            log.info("支付回调trade_status=" + trade_status);
            if (AlipayNotify.verify(params)) {//验证成功
                log.info("支付回调验签成功");

                if (trade_status.equals("TRADE_FINISHED")) {
                    log.info("支付宝非第一次回调");
                } else if (trade_status.equals("TRADE_SUCCESS")) {
                    if (aliPayService.isFinish(params)) {
                        log.info("订单已经处理完成,订单号:" + params.get("out_trade_no"));
                    } else {
                        aliPayService.paySuccess(params);
                    }
                }
                log.debug("支付回调处理成功,返回SUCCESS");
                return SUCCESS;
            } else {
                log.error("支付回调验签失败");
                return FAIL;
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.error("支付回调验签失败" + e.getMessage());
            return FAIL;
        }

    }

    @TokenAnnotation(required = true)
    @RequestMapping(value = "/order", method = RequestMethod.POST)
    @ApiOperation(value = "下单接口")
    @ResponseBody
    public Map<String, String> saveOrder(@RequestBody() OrderRequest orderRequest) throws BaseException {

        Map<String, String> requestMap = new HashMap<String, String>();
        AuthInfo authInfo = AuthUtil.getAuthInfo(getRequest());

        Map<String, String> paramsMap = new HashMap<String, String>();
        String orderCode = DateUtils.getOrderCode();//获取订单编号
        paramsMap.put("app_id", orderRequest.getApp_id());
        paramsMap.put("appenv", orderRequest.getAppenv());
        paramsMap.put("out_trade_no", orderCode);
        paramsMap.put("subject", orderRequest.getSubject());
        paramsMap.put("payment_type", orderRequest.getPayment_type());
        paramsMap.put("total_fee", "0.01");
        paramsMap.put("body", orderRequest.getBody());

        TOrder order = new TOrder();
        order.setOrderCode(orderCode);
        order.setHospitalid(authInfo.getAppIntId());
        order.setUserid(authInfo.getUserIntId());
        order.setPaymentType(orderRequest.getProductType());
        order.setProductId(orderRequest.getProductId());
        order.setProductCode(orderRequest.getProductCode());
        order.setTotalFee(orderRequest.getTotal_fee());
        order.setTradeStatus(OrderTradeStatus.WAIT_BUYER_PAY);
        order.setState(OrderState.START_STATE);
        orderService.insertSelective(order);
        requestMap.put("orderCode", paramsMap.get("out_trade_no"));
        requestMap.put("requestStr", AlipayNotify.addSign(paramsMap));
        return requestMap;
    }
}
```

# FAQ

Q: Android如何调试？

A: 如果怀疑插件有BUG，请使用tag名称为cordova-alipay-base查看日志。

Q: Windows 版本？

A: 这个很抱歉，有个哥们买了Lumia之后一直在抱怨应用太少，你也很不幸，有这个需求：） 欢迎 pull request.


# TODO

# 许可证

[MIT LICENSE](http://opensource.org/licenses/MIT)
