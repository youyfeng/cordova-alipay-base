package com.xc.freeapp.controller;

import com.alibaba.fastjson.JSONObject;
import com.github.miemiedev.mybatis.paginator.domain.PageBounds;
import com.github.miemiedev.mybatis.paginator.domain.PageList;
import com.github.miemiedev.mybatis.paginator.domain.Paginator;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.xc.freeapp.bean.AuthInfo;
import com.xc.freeapp.common.OrderState;
import com.xc.freeapp.common.OrderTradeStatus;
import com.xc.freeapp.common.ResponsePage;
import com.xc.freeapp.entity.TOrder;
import com.xc.freeapp.exception.BaseException;
import com.xc.freeapp.filter.TokenAnnotation;
import com.xc.freeapp.request.OrderRequest;
import com.xc.freeapp.service.AliPayService;
import com.xc.freeapp.service.InquisitionService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
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

    @Autowired
    InquisitionService inquisitionService;


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
                        inquisitionService.sendNoticeTofreeweb(params.get("out_trade_no"));
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
    public Map<String, String> saveOrder(@RequestBody  @Valid OrderRequest orderRequest) throws Exception {

        Map<String, String> requestMap = new HashMap<String, String>();
        AuthInfo authInfo = AuthUtil.getAuthInfo(getRequest());

        Map<String, String> paramsMap = new HashMap<String, String>();
        String orderCode = DateUtils.getOrderCode();//获取订单编号
        paramsMap.put("app_id", orderRequest.getApp_id());
        paramsMap.put("appenv", orderRequest.getAppenv());
        paramsMap.put("out_trade_no", orderCode);
        paramsMap.put("subject", orderRequest.getSubject());
        paramsMap.put("payment_type", orderRequest.getPayment_type());
        paramsMap.put("total_fee", orderRequest.getTotal_fee()+"");
        paramsMap.put("body", orderRequest.getBody());

        TOrder order = new TOrder();
        order.setOrderCode(orderCode);
        order.setGmtCreate(new Date());
        order.setHospitalid(authInfo.getAppIntId());
        order.setUserid(authInfo.getUserIntId());
        order.setPaymentType(Integer.valueOf(orderRequest.getPayment_type()));
        order.setProductType(orderRequest.getProductType());
        order.setProductId(orderRequest.getProductId());
        order.setProductCode(orderRequest.getProductCode());
        order.setTotalFee(orderRequest.getTotal_fee());
        order.setTradeStatus(OrderTradeStatus.WAIT_BUYER_PAY);
        order.setState(OrderState.START_STATE);
        orderService.insertSelective(order);


        aliPayService.orderSuccess(order);

        if (orderRequest.getTotal_fee()==0){
            Map<String ,String> map =new HashMap<String, String>();
            map.put("out_trade_no",order.getOrderCode());
            map.put("total_fee",orderRequest.getTotal_fee()+"");
            aliPayService.paySuccess(map);
            inquisitionService.sendNoticeTofreeweb( map.get("out_trade_no"));
        }

        requestMap.put("orderCode", paramsMap.get("out_trade_no"));
        requestMap.put("orderId",order.getId().toString());
        requestMap.put("requestStr", AlipayNotify.addSign(paramsMap));
        return requestMap;
    }
    
    /**
	 * 诊金列表接口(医生账号专用)
	 * 
	 * @param page
	 * @param limit
	 * @return
	 * @throws BaseException
	 */
	@TokenAnnotation(required = true)
	@RequestMapping(value = "myOrders", method = RequestMethod.GET)
	@ResponseBody
	@ApiOperation(value = "查询诊金列表")
	public ResponsePage<List<TOrder>> selectMyOrders(
			@RequestParam(value = "page", required = false, defaultValue = "0") Integer page,
			@RequestParam(value = "limit", required = false, defaultValue = "15") Integer limit) throws BaseException {
		AuthInfo authInfo = AuthUtil.getAuthInfo(getRequest());
		PageBounds pageBounds = new PageBounds(page, limit);
		// 获取问诊信息
		List<TOrder> orders = orderService.selectMyOrders(authInfo.getDoctorId(), authInfo.getAppId(), pageBounds);
		Paginator paginator = ((PageList) orders).getPaginator();
		return new ResponsePage<List<TOrder>>(orders, paginator);
	}

}