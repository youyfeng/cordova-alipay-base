package com.xc.freeapp.request;

import com.wordnik.swagger.annotations.ApiModelProperty;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.NotNull;


/**
 * Created by guoshuaipeng on 2016/5/11.
 */
public class OrderRequest {

  @NotNull
  @ApiModelProperty(value="商品类型(1.预约挂号,2.在线问诊) ")
  private Integer productType;
  @NotNull
  @ApiModelProperty(value="商品ID(医生id)")
  private Integer productId;
  @NotNull
  @ApiModelProperty(value="商品编号")
  private Integer productCode;

  public Integer getProductCode() {
    return productCode;
  }

  public void setProductCode(Integer productCode) {
    this.productCode = productCode;
  }

  @ApiModelProperty(value="客户端号  可空")
  private String app_id;
  @ApiModelProperty(value=" 客户端来源 可空")
  private String appenv;
  @NotEmpty(message="商品名称不能为空")
  @ApiModelProperty(value=" 商品名称")
  private String subject;
  @NotEmpty(message=" 支付类型不能为空")
  @ApiModelProperty(value="  支付类型。默认值为：1（商品购买）。")
  private String payment_type;
  @NotNull(message=" 总金额 不能为空")
  @ApiModelProperty(value="总金额 该笔订单的资金总额，单位为RMB-Yuan。取值范围为[0.01，100000000.00]，精确到小数点后两位。")
  private double total_fee;
  @NotEmpty(message=" 商品详情不能为空")
  @ApiModelProperty(value="商品详情")
  private String body;
  public Integer getProductType() {
    return productType;
  }

  public void setProductType(Integer productType) {
    this.productType = productType;
  }

  public Integer getProductId() {
    return productId;
  }

  public void setProductId(Integer productId) {
    this.productId = productId;
  }

  public double getTotal_fee() {
    return total_fee;
  }

  public void setTotal_fee(double total_fee) {
    this.total_fee = total_fee;
  }

  public String getApp_id() {
    return app_id;
  }

  public void setApp_id(String app_id) {
    this.app_id = app_id;
  }

  public String getAppenv() {
    return appenv;
  }

  public void setAppenv(String appenv) {
    this.appenv = appenv;
  }

  public String getSubject() {
    return subject;
  }

  public void setSubject(String subject) {
    this.subject = subject;
  }

  public String getPayment_type() {
    return payment_type;
  }

  public void setPayment_type(String payment_type) {
    this.payment_type = payment_type;
  }

  public String getBody() {
    return body;
  }

  public void setBody(String body) {
    this.body = body;
  }
}
