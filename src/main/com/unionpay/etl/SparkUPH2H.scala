package com.unionpay.etl
import java.text.SimpleDateFormat

import com.unionpay.conf.ConfigurationManager
import com.unionpay.constant.Constants
import com.unionpay.jdbc.UPSQL_TIMEPARAMS_JDBC
import com.unionpay.utils.DateUtils
import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.hive.HiveContext
import org.apache.spark.{SparkConf, SparkContext}
import org.joda.time.format.DateTimeFormat
import org.joda.time.{DateTime, Days, LocalDate}

/**
  * 作业：抽取银联Hive的数据到钱包Hive数据仓库
  */
object SparkUPH2H {
  //UP NAMENODE URL
  private val up_namenode=ConfigurationManager.getProperty(Constants.UP_NAMENODE)
  //UP HIVE DATA ROOT URL
  private val up_hivedataroot=ConfigurationManager.getProperty(Constants.UP_HIVEDATAROOT)
  //指定HIVE数据库名
  private lazy val hive_dbname =ConfigurationManager.getProperty(Constants.HIVE_DBNAME)
  private  lazy  val dateFormatter=DateTimeFormat.forPattern("yyyy-MM-dd")
  private  lazy  val dateFormat_2=DateTimeFormat.forPattern("yyyyMMdd")

  def main(args: Array[String]): Unit = {
    val conf = new SparkConf().setAppName("SparkUPH2H")
    val sc = new SparkContext(conf)
    sc.setLogLevel("ERROR")
    implicit val sqlContext = new HiveContext(sc)

    val rowParams=UPSQL_TIMEPARAMS_JDBC.readTimeParams(sqlContext)
    val start_dt=DateUtils.getYesterdayByJob(rowParams.getString(0))  //获取开始日期：start_dt-1
    val end_dt=rowParams.getString(1)//结束日期
    val interval=DateUtils.getIntervalDays(start_dt,end_dt).toInt

    println(s"#### SparkUPH2H 数据抽取的起始日期为: $start_dt --  $end_dt")

    val JobName = if(args.length>0) args(0) else None
    println(s"#### The Current Job Name is ： [$JobName]")
    JobName match {
      /**
        * 每日模板job
        */
      case "JOB_HV_39"  => JOB_HV_39(sqlContext,end_dt) //CODE BY YX
      case "JOB_HV_49"  => JOB_HV_49 //CODE BY YX
      case "JOB_HV_52"  => JOB_HV_52(sqlContext,end_dt) //CODE BY YX
      case "JOB_HV_55"  =>  JOB_HV_55(sqlContext,start_dt,end_dt) //CODE BY TZQ
      case "JOB_HV_56"  =>  JOB_HV_56(sqlContext,start_dt,end_dt) //CODE BY TZQ
      case "JOB_HV_57"  =>  JOB_HV_57(sqlContext,start_dt,end_dt) //CODE BY TZQ


      /**
        * 指标套表job
        */
      case "JOB_HV_41"  => JOB_HV_41(sqlContext, start_dt, end_dt, interval) //CODE BY XTP   already formatted
      case "JOB_HV_50"  =>  JOB_HV_50(sqlContext,start_dt,end_dt) //CODE BY XTP
      case "JOB_HV_51"  =>  JOB_HV_51(sqlContext,start_dt,end_dt) //CODE BY XTP
      case "JOB_HV_58"  =>  JOB_HV_58(sqlContext,start_dt,end_dt) //CODE BY XTP
      case "JOB_HV_59"  =>  JOB_HV_59(sqlContext,start_dt,end_dt) //CODE BY XTP
      case "JOB_HV_60"  =>  JOB_HV_60(sqlContext,start_dt,end_dt) //CODE BY XTP
      case "JOB_HV_61"  =>  JOB_HV_61(sqlContext,start_dt,end_dt) //CODE BY XTP
      case "JOB_HV_62"  =>  JOB_HV_62(sqlContext,start_dt,end_dt) //CODE BY XTP
      case "JOB_HV_63"  =>  JOB_HV_63(sqlContext,start_dt,end_dt) //CODE BY XTP
      case "JOB_HV_64"  =>  JOB_HV_64(sqlContext,start_dt,end_dt) //CODE BY XTP
      case "JOB_HV_65"  =>  JOB_HV_65(sqlContext,start_dt,end_dt) //CODE BY XTP
      case "JOB_HV_71"  =>  JOB_HV_71(sqlContext,start_dt,end_dt) //CODE BY TZQ

      case _ => println("#### No Case Job,Please Input JobName")

    }

    sc.stop()
  }


  /**
    * JobName: JOB_HV_39
    * Feature: uphive.rtdtrs_dtl_achis -> hive.hive_achis_trans
    *
    * @author YangXue
    * @time 2016-08-30
    * @param sqlContext,end_dt
    */

  def JOB_HV_39(implicit sqlContext: HiveContext,end_dt:String) = {
    println("#### JOB_HV_39(rtdtrs_dtl_achis -> hive_achis_trans)")

    val today_dt = end_dt
    println("#### JOB_HV_39 增量抽取的时间范围为: "+end_dt)

    DateUtils.timeCost("JOB_HV_39") {
      val df = sqlContext.read.parquet(s"$up_namenode/$up_hivedataroot/incident/ods/hive_achis_trans/part_settle_dt=$today_dt")
      println(s"#### JOB_HV_39 read $up_namenode/ 数据完成时间为:"+DateUtils.getCurrentSystemTime())

      df.registerTempTable("spark_hive_achis_trans")
      println("#### JOB_HV_39 registerTempTable--spark_hive_achis_trans 完成的系统时间为:"+DateUtils.getCurrentSystemTime())

      if(!Option(df).isEmpty){
        sqlContext.sql(s"use $hive_dbname")
        sqlContext.sql(
          s"""
             |insert overwrite table hive_achis_trans partition (part_settle_dt)
             |select
             |settle_dt          ,
             |trans_idx          ,
             |trans_tp           ,
             |trans_class        ,
             |trans_source       ,
             |buss_chnl          ,
             |carrier_tp         ,
             |pri_acct_no        ,
             |mchnt_conn_tp      ,
             |access_tp          ,
             |conn_md            ,
             |acq_ins_id_cd      ,
             |acq_head           ,
             |fwd_ins_id_cd      ,
             |rcv_ins_id_cd      ,
             |iss_ins_id_cd      ,
             |iss_head           ,
             |iss_head_nm        ,
             |mchnt_cd           ,
             |mchnt_nm           ,
             |mchnt_country      ,
             |mchnt_url          ,
             |mchnt_front_url    ,
             |mchnt_back_url     ,
             |mchnt_tp           ,
             |mchnt_order_id     ,
             |mchnt_order_desc   ,
             |mchnt_add_info     ,
             |mchnt_reserve      ,
             |reserve            ,
             |sub_mchnt_cd       ,
             |sub_mchnt_company  ,
             |sub_mchnt_nm       ,
             |mchnt_class        ,
             |sys_tra_no         ,
             |trans_tm           ,
             |sys_tm             ,
             |trans_dt           ,
             |auth_id            ,
             |trans_at           ,
             |trans_curr_cd      ,
             |proc_st            ,
             |resp_cd            ,
             |proc_sys           ,
             |trans_no           ,
             |trans_st           ,
             |conv_dt            ,
             |settle_at          ,
             |settle_curr_cd     ,
             |settle_conv_rt     ,
             |cert_tp            ,
             |cert_id            ,
             |name               ,
             |phone_no           ,
             |usr_id             ,
             |mchnt_id           ,
             |pay_method         ,
             |trans_ip           ,
             |encoding           ,
             |mac_addr           ,
             |card_attr          ,
             |ebank_id           ,
             |ebank_mchnt_cd     ,
             |ebank_order_num    ,
             |ebank_idx          ,
             |ebank_rsp_tm       ,
             |kz_curr_cd         ,
             |kz_conv_rt         ,
             |kz_at              ,
             |delivery_country   ,
             |delivery_province  ,
             |delivery_city      ,
             |delivery_district  ,
             |delivery_street    ,
             |sms_tp             ,
             |sign_method        ,
             |verify_mode        ,
             |accpt_pos_id       ,
             |mer_cert_id        ,
             |cup_cert_id        ,
             |mchnt_version      ,
             |sub_trans_tp       ,
             |mac                ,
             |biz_tp             ,
             |source_idt         ,
             |delivery_risk      ,
             |trans_flag         ,
             |org_trans_idx      ,
             |org_sys_tra_no     ,
             |org_sys_tm         ,
             |org_mchnt_order_id ,
             |org_trans_tm       ,
             |org_trans_at       ,
             |req_pri_data       ,
             |pri_data           ,
             |addn_at            ,
             |res_pri_data       ,
             |inq_dtl            ,
             |reserve_fld        ,
             |buss_code          ,
             |t_mchnt_cd         ,
             |is_oversea         ,
             |points_at          ,
             |pri_acct_tp        ,
             |area_cd            ,
             |mchnt_fee_at       ,
             |user_fee_at        ,
             |curr_exp           ,
             |rcv_acct           ,
             |track2             ,
             |track3             ,
             |customer_nm        ,
             |product_info       ,
             |customer_email     ,
             |cup_branch_ins_cd  ,
             |org_trans_dt       ,
             |special_calc_cost  ,
             |zero_cost          ,
             |advance_payment    ,
             |new_trans_tp       ,
             |flight_inf         ,
             |md_id              ,
             |ud_id              ,
             |syssp_id           ,
             |card_sn            ,
             |tfr_in_acct        ,
             |acct_id            ,
             |card_bin           ,
             |icc_data           ,
             |icc_data2          ,
             |card_seq_id        ,
             |pos_entry_cd       ,
             |pos_cond_cd        ,
             |term_id            ,
             |usr_num_tp         ,
             |addn_area_cd       ,
             |usr_num            ,
             |reserve1           ,
             |reserve2           ,
             |reserve3           ,
             |reserve4           ,
             |reserve5           ,
             |reserve6           ,
             |rec_st             ,
             |comments           ,
             |to_ts              ,
             |rec_crt_ts        ,
             |rec_upd_ts        ,
             |pay_acct          ,
             |trans_chnl        ,
             |tlr_st            ,
             |rvs_st            ,
             |out_trans_tp      ,
             |org_out_trans_tp  ,
             |bind_id           ,
             |ch_info           ,
             |card_risk_flag    ,
             |trans_step        ,
             |ctrl_msg          ,
             |mchnt_delv_tag    ,
             |mchnt_risk_tag    ,
             |bat_id            ,
             |payer_ip          ,
             |gt_sign_val       ,
             |mchnt_sign_val    ,
             |deduction_at      ,
             |src_sys_flag      ,
             |mac_ip            ,
             |mac_sq            ,
             |trans_ip_num      ,
             |cvn_flag          ,
             |expire_flag       ,
             |usr_inf           ,
             |imei              ,
             |iss_ins_tp        ,
             |dir_field         ,
             |buss_tp           ,
             |in_trans_tp       ,
             |to_date(settle_dt) as p_settle_dt
             |from
             |spark_hive_achis_trans
           """.stripMargin)
        println("#### JOB_HV_39 分区数据插入完成的时间为："+DateUtils.getCurrentSystemTime())
      } else {
        println(s"#### JOB_HV_39 read $up_namenode/ 无数据！")
      }
    }
  }


  /**
    * hive-job-41 2016-11-03
    * rtdtrs_dtl_cups to hive_cups_trans
    *
    * @author Xue
    * @param sqlContext
    */
  def JOB_HV_41(implicit sqlContext: HiveContext,start_dt:String,end_dt: String, interval: Int) {
    println("#### JOB_HV_41(rtdtrs_dtl_cups -> hive_cups_trans)")

    var today_dt = start_dt
    if (interval > 0) {
      println("#### JOB_HV_41 增量抽取的时间范围为: "+start_dt+ "-"+ end_dt)
      DateUtils.timeCost("JOB_HV_41") {
        val df = sqlContext.read.parquet(s"$up_namenode/$up_hivedataroot/incident/ods/hive_cups_trans/part_settle_dt=$today_dt")
        println(s"#### JOB_HV_41 read $up_namenode/ 数据完成时间为:" + DateUtils.getCurrentSystemTime())

        df.registerTempTable("spark_hive_cups_trans")
        println("#### JOB_HV_41 registerTempTable--spark_hive_cups_trans 完成的系统时间为:" + DateUtils.getCurrentSystemTime())

        if (!Option(df).isEmpty) {
          sqlContext.sql(s"use $hive_dbname")
          sqlContext.sql(
            s"""
               |insert overwrite table hive_cups_trans partition (part_settle_dt)
               |select
               |settle_dt                        ,
               |pri_key                          ,
               |log_cd                           ,
               |settle_tp                        ,
               |settle_cycle                     ,
               |block_id                         ,
               |orig_key                         ,
               |related_key                      ,
               |trans_fwd_st                     ,
               |trans_rcv_st                     ,
               |sms_dms_conv_in                  ,
               |fee_in                           ,
               |cross_dist_in                    ,
               |orig_acpt_sdms_in                ,
               |tfr_in_in                        ,
               |trans_md                         ,
               |source_region_cd                 ,
               |dest_region_cd                   ,
               |cups_card_in                     ,
               |cups_sig_card_in                 ,
               |card_class                       ,
               |card_attr                        ,
               |sti_in                           ,
               |trans_proc_in                    ,
               |acq_ins_id_cd                    ,
               |acq_ins_tp                       ,
               |fwd_ins_id_cd                    ,
               |fwd_ins_tp                       ,
               |rcv_ins_id_cd                    ,
               |rcv_ins_tp                       ,
               |iss_ins_id_cd                    ,
               |iss_ins_tp                       ,
               |related_ins_id_cd                ,
               |related_ins_tp                   ,
               |acpt_ins_id_cd                   ,
               |acpt_ins_tp                      ,
               |pri_acct_no                      ,
               |pri_acct_no_conv                 ,
               |sys_tra_no                       ,
               |sys_tra_no_conv                  ,
               |sw_sys_tra_no                    ,
               |auth_dt                          ,
               |auth_id_resp_cd                  ,
               |resp_cd1                         ,
               |resp_cd2                         ,
               |resp_cd3                         ,
               |resp_cd4                         ,
               |cu_trans_st                      ,
               |sti_takeout_in                   ,
               |trans_id                         ,
               |trans_tp                         ,
               |trans_chnl                       ,
               |card_media                       ,
               |card_media_proc_md               ,
               |card_brand                       ,
               |expire_seg                       ,
               |trans_id_conv                    ,
               |settle_mon                       ,
               |settle_d                         ,
               |orig_settle_dt                   ,
               |settle_fwd_ins_id_cd             ,
               |settle_rcv_ins_id_cd             ,
               |trans_at                         ,
               |orig_trans_at                    ,
               |trans_conv_rt                    ,
               |trans_curr_cd                    ,
               |cdhd_fee_at                      ,
               |cdhd_fee_conv_rt                 ,
               |cdhd_fee_acct_curr_cd            ,
               |repl_at                          ,
               |exp_snd_chnl                     ,
               |confirm_exp_chnl                 ,
               |extend_inf                       ,
               |conn_md                          ,
               |msg_tp                           ,
               |msg_tp_conv                      ,
               |card_bin                         ,
               |related_card_bin                 ,
               |trans_proc_cd                    ,
               |trans_proc_cd_conv               ,
               |tfr_dt_tm                        ,
               |loc_trans_tm                     ,
               |loc_trans_dt                     ,
               |conv_dt                          ,
               |mchnt_tp                         ,
               |pos_entry_md_cd                  ,
               |card_seq                         ,
               |pos_cond_cd                      ,
               |pos_cond_cd_conv                 ,
               |retri_ref_no                     ,
               |term_id                          ,
               |term_tp                          ,
               |mchnt_cd                         ,
               |card_accptr_nm_addr              ,
               |ic_data                          ,
               |rsn_cd                           ,
               |addn_pos_inf                     ,
               |orig_msg_tp                      ,
               |orig_msg_tp_conv                 ,
               |orig_sys_tra_no                  ,
               |orig_sys_tra_no_conv             ,
               |orig_tfr_dt_tm                   ,
               |related_trans_id                 ,
               |related_trans_chnl               ,
               |orig_trans_id                    ,
               |orig_trans_id_conv               ,
               |orig_trans_chnl                  ,
               |orig_card_media                  ,
               |orig_card_media_proc_md          ,
               |tfr_in_acct_no                   ,
               |tfr_out_acct_no                  ,
               |cups_resv                        ,
               |ic_flds                          ,
               |cups_def_fld                     ,
               |spec_settle_in                   ,
               |settle_trans_id                  ,
               |spec_mcc_in                      ,
               |iss_ds_settle_in                 ,
               |acq_ds_settle_in                 ,
               |settle_bmp                       ,
               |upd_in                           ,
               |exp_rsn_cd                       ,
               |to_ts                            ,
               |resnd_num                        ,
               |pri_cycle_no                     ,
               |alt_cycle_no                     ,
               |corr_pri_cycle_no                ,
               |corr_alt_cycle_no                ,
               |disc_in                          ,
               |vfy_rslt                         ,
               |vfy_fee_cd                       ,
               |orig_disc_in                     ,
               |orig_disc_curr_cd                ,
               |fwd_settle_at                    ,
               |rcv_settle_at                    ,
               |fwd_settle_conv_rt               ,
               |rcv_settle_conv_rt               ,
               |fwd_settle_curr_cd               ,
               |rcv_settle_curr_cd               ,
               |disc_cd                          ,
               |allot_cd                         ,
               |total_disc_at                    ,
               |fwd_orig_settle_at               ,
               |rcv_orig_settle_at               ,
               |vfy_fee_at                       ,
               |sp_mchnt_cd                      ,
               |acct_ins_id_cd                   ,
               |iss_ins_id_cd1                   ,
               |iss_ins_id_cd2                   ,
               |iss_ins_id_cd3                   ,
               |iss_ins_id_cd4                   ,
               |mchnt_ins_id_cd1                 ,
               |mchnt_ins_id_cd2                 ,
               |mchnt_ins_id_cd3                 ,
               |mchnt_ins_id_cd4                 ,
               |term_ins_id_cd1                  ,
               |term_ins_id_cd2                  ,
               |term_ins_id_cd3                  ,
               |term_ins_id_cd4                  ,
               |term_ins_id_cd5                  ,
               |acpt_cret_disc_at                ,
               |acpt_debt_disc_at                ,
               |iss1_cret_disc_at                ,
               |iss1_debt_disc_at                ,
               |iss2_cret_disc_at                ,
               |iss2_debt_disc_at                ,
               |iss3_cret_disc_at                ,
               |iss3_debt_disc_at                ,
               |iss4_cret_disc_at                ,
               |iss4_debt_disc_at                ,
               |mchnt1_cret_disc_at              ,
               |mchnt1_debt_disc_at              ,
               |mchnt2_cret_disc_at              ,
               |mchnt2_debt_disc_at              ,
               |mchnt3_cret_disc_at              ,
               |mchnt3_debt_disc_at              ,
               |mchnt4_cret_disc_at              ,
               |mchnt4_debt_disc_at              ,
               |term1_cret_disc_at               ,
               |term1_debt_disc_at               ,
               |term2_cret_disc_at               ,
               |term2_debt_disc_at               ,
               |term3_cret_disc_at               ,
               |term3_debt_disc_at               ,
               |term4_cret_disc_at               ,
               |term4_debt_disc_at               ,
               |term5_cret_disc_at               ,
               |term5_debt_disc_at               ,
               |pay_in                           ,
               |exp_id                           ,
               |vou_in                           ,
               |orig_log_cd                      ,
               |related_log_cd                   ,
               |mdc_key                          ,
               |rec_upd_ts                       ,
               |rec_crt_ts                       ,
               |hp_settle_dt                     ,
               |hp_settle_dt
               |from
               |spark_hive_cups_trans
           """.stripMargin)
          println("#### JOB_HV_41 分区数据插入完成的时间为：" + DateUtils.getCurrentSystemTime())
        } else {
          println(s"#### JOB_HV_41 read $up_namenode/ 无数据！")
        }
        today_dt = DateUtils.addOneDay(today_dt)
      }
    }
  }


  /**
    * JobName: JOB_HV_49
    * Feature: uphive.rtapam_prv_ucbiz_cdhd_bas_inf -> hive_ucbiz_cdhd_bas_inf
    *
    * @author YangXue
    * @time 2016-09-14
    * @param sqlContext
    */
  def JOB_HV_49(implicit sqlContext: HiveContext) = {
    println("#### JOB_HV_49(rtapam_prv_ucbiz_cdhd_bas_inf -> hive_ucbiz_cdhd_bas_inf)")
    println("#### JOB_HV_49 为全量抽取的表")

    DateUtils.timeCost("JOB_HV_49"){
      val df = sqlContext.read.parquet(s"$up_namenode/$up_hivedataroot/participant/user/hive_ucbiz_cdhd_bas_inf")
      println(s"#### JOB_HV_49 read $up_namenode/ 数据完成时间为:"+DateUtils.getCurrentSystemTime())

      df.registerTempTable("spark_ucbiz_cdhd_bas_inf")
      println("#### JOB_HV_49 registerTempTable--spark_ucbiz_cdhd_bas_inf 完成的系统时间为:"+DateUtils.getCurrentSystemTime())

      if(!Option(df).isEmpty){
        sqlContext.sql(s"use $hive_dbname")
        sqlContext.sql("insert overwrite table hive_ucbiz_cdhd_bas_inf select * from spark_ucbiz_cdhd_bas_inf")
        println("#### JOB_HV_49 全量数据插入完成的时间为："+DateUtils.getCurrentSystemTime())
      }else{
        println(s"#### JOB_HV_49 read $up_namenode/ 无数据！")
      }
    }
  }


  /**
    * hive-job-50 2016-11-03
    * org_tdapp_keyvalue to hive_org_tdapp_keyvalue
    *
    * @author Xue
    * @param sqlContext
    */

    // Xue create function about partition by date ^_^
    def JOB_HV_50(implicit sqlContext: HiveContext,start_dt: String, end_dt: String)  {

      var sdf: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd")
      val start = LocalDate.parse(start_dt, dateFormatter)
      val end = LocalDate.parse(end_dt, dateFormatter)
      val days = Days.daysBetween(start, end).getDays
      val dateStrs = for (day <- 0 to days) {
        val currentDay = (start.plusDays(day).toString(dateFormatter))
        println("######JOB_HV_50######")
        val df = sqlContext.read.parquet(s"$up_namenode/$up_hivedataroot/incident/td/hive_org_tdapp_keyvalue/part_updays=$currentDay")
        println(s"###### read $up_namenode/ successful ######")
        df.registerTempTable("spark_hive_org_tdapp_keyvalue")

        val daytime:DataFrame = sqlContext.sql(
          s"""
              |select distinct
              |daytime
              |from spark_hive_org_tdapp_keyvalue
            """.stripMargin
            )
        daytime.show()

        val time_lsit = daytime.select("daytime").rdd.map(r => r(0).asInstanceOf[String]).collect().toList

        increase_ListBuffer(time_lsit)

        def increase_ListBuffer(list:List[String]) :List[String]={
          import scala.collection.mutable.ListBuffer
          var result = ListBuffer[String]()
          for(element <- list){
            val dt = DateTime.parse(element,dateFormat_2)
            val days = element
            val days_fmt = dateFormatter.print(dt)
            sqlContext.sql(s"use $hive_dbname")
            sqlContext.sql(s"alter table hive_org_tdapp_keyvalue drop partition (part_daytime='$days_fmt',part_updays='$currentDay')")
            println(s"alter table hive_org_tdapp_keyvalue drop partition (part_daytime='$days_fmt',part_updays='$currentDay') successfully!")
            sqlContext.sql(s"insert into hive_org_tdapp_keyvalue partition (part_daytime='$days_fmt',part_updays='$currentDay') select * from spark_hive_org_tdapp_keyvalue htempa where htempa.daytime='$days'")
            println(s"insert into hive_org_tdapp_keyvalue partition (part_daytime='$days_fmt',part_updays='$currentDay') successfully!")
            result += element+1
          }
          result.toList
        }
      }
    }


  /**
    * hive-job-51 2016-12-27
    * org_tdapp_tappevent to hive_org_tdapp_tappevent
    *
    * @author Xue
    * @param sqlContext
    */
  def JOB_HV_51(implicit sqlContext: HiveContext,start_dt: String,end_dt:String) = {
    val start = LocalDate.parse(start_dt, dateFormatter)
    val end = LocalDate.parse(end_dt, dateFormatter)
    val days = Days.daysBetween(start, end).getDays
    for (day <- 0 to days) {
      val currentDay = (start.plusDays(day).toString(dateFormatter))
      println("######JOB_HV_51######")
      val df = sqlContext.read.parquet(s"$up_namenode/$up_hivedataroot/incident/td/hive_org_tdapp_tappevent/part_updays=$currentDay")
      println(s"###### read $up_namenode/ successful ######")
      df.registerTempTable("spark_hive_org_tdapp_tappevent")

      val daytime:DataFrame = sqlContext.sql(
        s"""
           |select distinct
           |daytime
           |from spark_hive_org_tdapp_tappevent
            """.stripMargin
      )
      val times = daytime.select("daytime").rdd.map(r => r(0).asInstanceOf[String]).collect().toList

      increase_ListBuffer(times)

      def increase_ListBuffer(list:List[String]) {

        for(element <- list){
          val dt = DateTime.parse(element,dateFormat_2)
          val days = element
          val days_fmt = dateFormatter.print(dt)

          sqlContext.sql(s"use $hive_dbname")
          sqlContext.sql(s"alter table hive_org_tdapp_tappevent drop partition (part_daytime='$days_fmt',part_updays='$currentDay')")
          println(s"alter table hive_org_tdapp_tappevent drop partition (part_daytime='$days_fmt',part_updays='$currentDay') successfully!")

          sqlContext.sql(s"insert into hive_org_tdapp_tappevent partition (part_daytime='$days_fmt',part_updays='$currentDay') select * from spark_hive_org_tdapp_tappevent hott where hott.daytime='$days'")
          println(s"insert into hive_org_tdapp_tappevent partition (part_daytime='$days_fmt',part_updays='$currentDay') successfully!")

        }
      }
    }
  }


  /**
    * JobName: JOB_HV_52
    * Feature: uphive.stmtrs_bsl_active_card_acq_branch_mon1 -> hive.hive_active_card_acq_branch_mon
    * 每月7、8、9三天抽取
    * @author YangXue
    * @time 2016-08-29
    * @param sqlContext,end_dt
    */
  def JOB_HV_52(implicit sqlContext: HiveContext, end_dt: String) = {
    println("#### JOB_HV_52(stmtrs_bsl_active_card_acq_branch_mon1 -> hive_active_card_acq_branch_mon)")

    val part_dt = end_dt.substring(0, 7)
    println("#### JOB_HV_52 增量抽取的时间范围为: " +part_dt)

    DateUtils.timeCost("JOB_HV_52") {
      val df = sqlContext.read.parquet(s"$up_namenode/$up_hivedataroot/product/card/hive_active_card_acq_branch_mon/part_settle_month=$part_dt")
      println(s"#### JOB_HV_52 read $up_namenode/ 数据完成时间为:" + DateUtils.getCurrentSystemTime())

      df.registerTempTable("spark_active_card_acq_branch_mon")
      println("#### JOB_HV_52 registerTempTable--spark_active_card_acq_branch_mon 完成的系统时间为:" + DateUtils.getCurrentSystemTime())

      if(!Option(df).isEmpty){
        sqlContext.sql(s"use $hive_dbname")
        sqlContext.sql(
          s"""
             |insert overwrite table hive_active_card_acq_branch_mon partition (part_settle_month)
             |select
             |trans_month,
             |trans_class,
             |trans_cd,
             |trans_chnl_id,
             |card_brand_id,
             |card_attr_id,
             |acq_intnl_org_id_cd,
             |iss_root_ins_id_cd,
             |active_card_num,
             |hp_settle_month,
             |hp_settle_month
             |from
             |spark_active_card_acq_branch_mon
           """.stripMargin)
        println("#### JOB_HV_52 分区数据插入完成的时间为："+DateUtils.getCurrentSystemTime())
      }else{
        println(s"#### JOB_HV_52 read $up_namenode/ 无数据！")
      }
    }
  }


  /**
    * hive-job-55 2016-11-15
    * org_tdapp_tactivity to  hive_org_tdapp_tactivity
    *
    * @author tzq
    * @param sqlContext
    */
  def JOB_HV_55(implicit sqlContext: HiveContext,start_dt: String,end_dt:String) = {
    val start = LocalDate.parse(start_dt, dateFormatter)
    val end = LocalDate.parse(end_dt, dateFormatter)
    val days = Days.daysBetween(start, end).getDays
    for (day <- 0 to days) {
      val currentDay = (start.plusDays(day).toString(dateFormatter))
      println("######JOB_HV_55######")
      val df = sqlContext.read.parquet(s"$up_namenode/$up_hivedataroot/incident/td/hive_org_tdapp_tactivity/part_updays=$currentDay")
      println(s"###### read $up_namenode/ successful ######")
      df.registerTempTable("spark_hive_org_tdapp_tactivity")

      val daytime:DataFrame = sqlContext.sql(
        s"""
           |select distinct
           |daytime
           |from spark_hive_org_tdapp_tactivity
            """.stripMargin
      )
      val times = daytime.select("daytime").rdd.map(r => r(0).asInstanceOf[String]).collect().toList

      increase_ListBuffer(times)

      def increase_ListBuffer(list:List[String]) {

        for(element <- list){
          val dt = DateTime.parse(element,dateFormat_2)
          val days = element
          val days_fmt = dateFormatter.print(dt)

          sqlContext.sql(s"use $hive_dbname")
          sqlContext.sql(s"alter table hive_org_tdapp_tactivity drop partition (part_daytime='$days_fmt',part_updays='$currentDay')")
          println(s"alter table hive_org_tdapp_tactivity drop partition (part_daytime='$days_fmt',part_updays='$currentDay') successfully!")

          sqlContext.sql(s"insert into hive_org_tdapp_tactivity partition (part_daytime='$days_fmt',part_updays='$currentDay') select * from spark_hive_org_tdapp_tactivity hott where hott.daytime='$days'")
          println(s"insert into hive_org_tdapp_tactivity partition (part_daytime='$days_fmt',part_updays='$currentDay') successfully!")

        }
      }
    }
  }

  /**
    * hive-job-56 2016-11-24
    * org_tdapp_tlaunch to  hive_org_tdapp_tlaunch
    *
    * @author tzq
    * @param sqlContext
    */
  def JOB_HV_56(implicit sqlContext: HiveContext,start_dt: String,end_dt:String) = {
    val start = LocalDate.parse(start_dt, dateFormatter)
    val end = LocalDate.parse(end_dt, dateFormatter)
    val days = Days.daysBetween(start, end).getDays
     for (day <- 0 to days) {
      val currentDay = (start.plusDays(day).toString(dateFormatter))
      println("######JOB_HV_56######")
      val df = sqlContext.read.parquet(s"$up_namenode/$up_hivedataroot/incident/td/hive_org_tdapp_tlaunch/part_updays=$currentDay")
      println(s"###### read $up_namenode/ successful ######")
      df.registerTempTable("spark_hive_org_tdapp_tlaunch")

      val daytime:DataFrame = sqlContext.sql(
        s"""
           |select distinct
           |daytime
           |from spark_hive_org_tdapp_tlaunch
            """.stripMargin
      )
      val times = daytime.select("daytime").rdd.map(r => r(0).asInstanceOf[String]).collect().toList

      increase_ListBuffer(times)

      def increase_ListBuffer(list:List[String]) {

        for(element <- list){
          val dt = DateTime.parse(element,dateFormat_2)
          val days = element
          val days_fmt = dateFormatter.print(dt)

          sqlContext.sql(s"use $hive_dbname")
          sqlContext.sql(s"alter table hive_org_tdapp_tlaunch drop partition (part_daytime='$days_fmt',part_updays='$currentDay')")
          println(s"alter table hive_org_tdapp_tlaunch drop partition (part_daytime='$days_fmt',part_updays='$currentDay') successfully!")
          sqlContext.sql(s"insert into hive_org_tdapp_tlaunch partition (part_daytime='$days_fmt',part_updays='$currentDay') select * from spark_hive_org_tdapp_tlaunch hott where hott.daytime='$days'")
          println(s"insert into hive_org_tdapp_tlaunch partition (part_daytime='$days_fmt',part_updays='$currentDay') successfully!")

        }
      }
    }
  }


  /**
    * hive-job-57   2016-11-24
    * org_tdapp_terminate to  hive_org_tdapp_terminate
    *
    * @author tzq
    * @param sqlContext
    */
  def JOB_HV_57(implicit sqlContext: HiveContext,start_dt: String,end_dt:String) = {
    val start = LocalDate.parse(start_dt, dateFormatter)
    val end = LocalDate.parse(end_dt, dateFormatter)
    val days = Days.daysBetween(start, end).getDays
    for (day <- 0 to days) {
      val currentDay = (start.plusDays(day).toString(dateFormatter))
      println("######JOB_HV_57######")
      val df = sqlContext.read.parquet(s"$up_namenode/$up_hivedataroot/incident/td/hive_org_tdapp_terminate/part_updays=$currentDay")
      println(s"###### read $up_namenode/ successful ######")
      df.registerTempTable("spark_hive_org_tdapp_terminate")

      val daytime:DataFrame = sqlContext.sql(
        s"""
           |select distinct
           |daytime
           |from spark_hive_org_tdapp_terminate
            """.stripMargin
      )
      val times = daytime.select("daytime").rdd.map(r => r(0).asInstanceOf[String]).collect().toList

      increase_ListBuffer(times)

      def increase_ListBuffer(list:List[String]) {

        for(element <- list){
          val dt = DateTime.parse(element,dateFormat_2)
          val days = element
          val days_fmt = dateFormatter.print(dt)

          sqlContext.sql(s"use $hive_dbname")
          sqlContext.sql(s"alter table hive_org_tdapp_terminate drop partition (part_daytime='$days_fmt',part_updays='$currentDay')")
          println(s"alter table hive_org_tdapp_terminate drop partition (part_daytime='$days_fmt',part_updays='$currentDay') successfully!")

          sqlContext.sql(s"insert into hive_org_tdapp_terminate partition (part_daytime='$days_fmt',part_updays='$currentDay') select * from spark_hive_org_tdapp_terminate hott where hott.daytime='$days'")
          println(s"insert into hive_org_tdapp_terminate partition (part_daytime='$days_fmt',part_updays='$currentDay') successfully!")

        }
      }
    }
  }


  /**
    * hive-job-58 2016-11-15
    * org_tdapp_activitynew to hive_org_tdapp_activitynew
    *
    * @author Xue
    * @param sqlContext
    */

  // Xue create function about partition by date ^_^
  def JOB_HV_58(implicit sqlContext: HiveContext,start_dt: String, end_dt: String)  {

    var sdf: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd")
    val start = LocalDate.parse(start_dt, dateFormatter)
    val end = LocalDate.parse(end_dt, dateFormatter)
    val days = Days.daysBetween(start, end).getDays
    val dateStrs = for (day <- 0 to days) {
      val currentDay = (start.plusDays(day).toString(dateFormatter))
      println("######JOB_HV_58######")
      val df = sqlContext.read.parquet(s"$up_namenode/$up_hivedataroot/incident/td/hive_org_tdapp_activitynew/part_updays=$currentDay")
      println(s"###### read $up_namenode/ successful ######")
      df.registerTempTable("spark_hive_org_tdapp_activitynew")

      val daytime:DataFrame = sqlContext.sql(
        s"""
           |select distinct
           |daytime
           |from spark_hive_org_tdapp_activitynew
            """.stripMargin
      )
      daytime.show(10)

      val time_lsit = daytime.select("daytime").rdd.map(r => r(0).asInstanceOf[String]).collect().toList

      increase_ListBuffer(time_lsit)

      def increase_ListBuffer(list:List[String]) :List[String]={
        import scala.collection.mutable.ListBuffer
        var result = ListBuffer[String]()
        for(element <- list){
          val dt = DateTime.parse(element,dateFormat_2)
          val days = element
          val days_fmt = dateFormatter.print(dt)
          sqlContext.sql(s"use $hive_dbname")
          sqlContext.sql(s"alter table hive_org_tdapp_activitynew drop partition (part_daytime='$days_fmt',part_updays='$currentDay')")
          println(s"alter table hive_org_tdapp_activitynew drop partition (part_daytime='$days_fmt',part_updays='$currentDay') successfully!")
          sqlContext.sql(s"insert into hive_org_tdapp_activitynew partition (part_daytime='$days_fmt',part_updays='$currentDay') select * from spark_hive_org_tdapp_activitynew htempa where htempa.daytime='$days'")
          println(s"insert into hive_org_tdapp_activitynew partition (part_daytime='$days_fmt',part_updays='$currentDay') successfully!")
          result += element+1
        }
        result.toList
      }
    }
  }


  /**
    * hive-job-59 2016-11-16
    * org_tdapp_devicenew to hive_org_tdapp_devicenew
    *
    * @author Xue
    * @param sqlContext
    */

  // Xue create function about partition by date ^_^
  def JOB_HV_59(implicit sqlContext: HiveContext,start_dt: String, end_dt: String)  {

    var sdf: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd")
    val start = LocalDate.parse(start_dt, dateFormatter)
    val end = LocalDate.parse(end_dt, dateFormatter)
    val days = Days.daysBetween(start, end).getDays
    val dateStrs = for (day <- 0 to days) {
      val currentDay = (start.plusDays(day).toString(dateFormatter))
      println("######JOB_HV_59######")
      val df = sqlContext.read.parquet(s"$up_namenode/$up_hivedataroot/incident/td/hive_org_tdapp_devicenew/part_updays=$currentDay")
      println(s"###### read $up_namenode/ successful ######")
      df.registerTempTable("spark_hive_org_tdapp_devicenew")

      val daytime:DataFrame = sqlContext.sql(
        s"""
           |select distinct
           |daytime
           |from spark_hive_org_tdapp_devicenew
            """.stripMargin
      )
      daytime.show(10)

      val time_lsit = daytime.select("daytime").rdd.map(r => r(0).asInstanceOf[String]).collect().toList

      increase_ListBuffer(time_lsit)

      def increase_ListBuffer(list:List[String]) :List[String]={
        import scala.collection.mutable.ListBuffer
        var result = ListBuffer[String]()
        for(element <- list){
          val dt = DateTime.parse(element,dateFormat_2)
          val days = element
          val days_fmt = dateFormatter.print(dt)
          sqlContext.sql(s"use $hive_dbname")
          sqlContext.sql(s"alter table hive_org_tdapp_devicenew drop partition (part_daytime='$days_fmt',part_updays='$currentDay')")
          println(s"alter table hive_org_tdapp_devicenew drop partition (part_daytime='$days_fmt',part_updays='$currentDay') successfully!")
          sqlContext.sql(s"insert into hive_org_tdapp_devicenew partition (part_daytime='$days_fmt',part_updays='$currentDay') select * from spark_hive_org_tdapp_devicenew htempa where htempa.daytime='$days'")
          println(s"insert into hive_org_tdapp_devicenew partition (part_daytime='$days_fmt',part_updays='$currentDay') successfully!")
          result += element+1
        }
        result.toList
      }
    }
  }


  /**
    * hive-job-60 2016-11-17
    * org_tdapp_eventnew to hive_org_tdapp_eventnew
    *
    * @author Xue
    * @param sqlContext
    */

  // Xue create function about partition by date ^_^
  def JOB_HV_60(implicit sqlContext: HiveContext,start_dt: String, end_dt: String)  {

    var sdf: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd")
    val start = LocalDate.parse(start_dt, dateFormatter)
    val end = LocalDate.parse(end_dt, dateFormatter)
    val days = Days.daysBetween(start, end).getDays
    val dateStrs = for (day <- 0 to days) {
      val currentDay = (start.plusDays(day).toString(dateFormatter))
      println("######JOB_HV_60######")
      val df = sqlContext.read.parquet(s"$up_namenode/$up_hivedataroot/incident/td/hive_org_tdapp_eventnew/part_updays=$currentDay")
      println(s"###### read $up_namenode/ successful ######")
      df.registerTempTable("spark_hive_org_tdapp_eventnew")

      val daytime:DataFrame = sqlContext.sql(
        s"""
           |select distinct
           |daytime
           |from spark_hive_org_tdapp_eventnew
            """.stripMargin
      )
      daytime.show(10)

      val time_lsit = daytime.select("daytime").rdd.map(r => r(0).asInstanceOf[String]).collect().toList

      increase_ListBuffer(time_lsit)

      def increase_ListBuffer(list:List[String]) :List[String]={
        import scala.collection.mutable.ListBuffer
        var result = ListBuffer[String]()
        for(element <- list){
          val dt = DateTime.parse(element,dateFormat_2)
          val days = element
          val days_fmt = dateFormatter.print(dt)
          sqlContext.sql(s"use $hive_dbname")
          sqlContext.sql(s"alter table hive_org_tdapp_eventnew drop partition (part_daytime='$days_fmt',part_updays='$currentDay')")
          println(s"alter table hive_org_tdapp_eventnew drop partition (part_daytime='$days_fmt',part_updays='$currentDay') successfully!")
          sqlContext.sql(s"insert into hive_org_tdapp_eventnew partition (part_daytime='$days_fmt',part_updays='$currentDay') select * from spark_hive_org_tdapp_eventnew htempa where htempa.daytime='$days'")
          println(s"insert into hive_org_tdapp_eventnew partition (part_daytime='$days_fmt',part_updays='$currentDay') successfully!")
          result += element+1
        }
        result.toList
      }
    }
  }


  /**
    * hive-job-61 2016-11-22
    * org_tdapp_exceptionnew to hive_org_tdapp_exceptionnew
    *
    * @author Xue
    * @param sqlContext
    */

  // Xue create function about partition by date ^_^
  def JOB_HV_61(implicit sqlContext: HiveContext,start_dt: String, end_dt: String)  {

    var sdf: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd")
    val start = LocalDate.parse(start_dt, dateFormatter)
    val end = LocalDate.parse(end_dt, dateFormatter)
    val days = Days.daysBetween(start, end).getDays
    val dateStrs = for (day <- 0 to days) {
      val currentDay = (start.plusDays(day).toString(dateFormatter))
      println("######JOB_HV_61######")
      val df = sqlContext.read.parquet(s"$up_namenode/$up_hivedataroot/incident/td/hive_org_tdapp_exceptionnew/part_updays=$currentDay")
      println(s"###### read $up_namenode/ successful ######")
      df.registerTempTable("spark_hive_org_tdapp_exceptionnew")

      val daytime:DataFrame = sqlContext.sql(
        s"""
           |select distinct
           |daytime
           |from spark_hive_org_tdapp_exceptionnew
            """.stripMargin
      )
      daytime.show(10)

      val time_lsit = daytime.select("daytime").rdd.map(r => r(0).asInstanceOf[String]).collect().toList

      increase_ListBuffer(time_lsit)

      def increase_ListBuffer(list:List[String]) :List[String]={
        import scala.collection.mutable.ListBuffer
        var result = ListBuffer[String]()
        for(element <- list){
          val dt = DateTime.parse(element,dateFormat_2)
          val days = element
          val days_fmt = dateFormatter.print(dt)
          sqlContext.sql(s"use $hive_dbname")
          sqlContext.sql(s"alter table hive_org_tdapp_exceptionnew drop partition (part_daytime='$days_fmt',part_updays='$currentDay')")
          println(s"alter table hive_org_tdapp_exceptionnew drop partition (part_daytime='$days_fmt',part_updays='$currentDay') successfully!")
          sqlContext.sql(s"insert into hive_org_tdapp_exceptionnew partition (part_daytime='$days_fmt',part_updays='$currentDay') select * from spark_hive_org_tdapp_exceptionnew htempa where htempa.daytime='$days'")
          println(s"insert into hive_org_tdapp_exceptionnew partition (part_daytime='$days_fmt',part_updays='$currentDay') successfully!")
          result += element+1
        }
        result.toList
      }
    }
  }


  /**
    * hive-job-62 2016-11-22
    * org_tdapp_tlaunchnew to hive_org_tdapp_tlaunchnew
    *
    * @author Xue
    * @param sqlContext
    */

  // Xue create function about partition by date ^_^
  def JOB_HV_62(implicit sqlContext: HiveContext,start_dt: String, end_dt: String)  {

    var sdf: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd")
    val start = LocalDate.parse(start_dt, dateFormatter)
    val end = LocalDate.parse(end_dt, dateFormatter)
    val days = Days.daysBetween(start, end).getDays
    val dateStrs = for (day <- 0 to days) {
      val currentDay = (start.plusDays(day).toString(dateFormatter))
      println("######JOB_HV_62######")
      val df = sqlContext.read.parquet(s"$up_namenode/$up_hivedataroot/incident/td/hive_org_tdapp_tlaunchnew/part_updays=$currentDay")
      println(s"###### read $up_namenode/ successful ######")
      df.registerTempTable("spark_hive_org_tdapp_tlaunchnew")

      val daytime:DataFrame = sqlContext.sql(
        s"""
           |select distinct
           |daytime
           |from spark_hive_org_tdapp_tlaunchnew
            """.stripMargin
      )
      daytime.show(10)

      val time_lsit = daytime.select("daytime").rdd.map(r => r(0).asInstanceOf[String]).collect().toList

      increase_ListBuffer(time_lsit)

      def increase_ListBuffer(list:List[String]) :List[String]={
        import scala.collection.mutable.ListBuffer
        var result = ListBuffer[String]()
        for(element <- list){
          val dt = DateTime.parse(element,dateFormat_2)
          val days = element
          val days_fmt = dateFormatter.print(dt)
          sqlContext.sql(s"use $hive_dbname")
          sqlContext.sql(s"alter table hive_org_tdapp_tlaunchnew drop partition (part_daytime='$days_fmt',part_updays='$currentDay')")
          println(s"alter table hive_org_tdapp_tlaunchnew drop partition (part_daytime='$days_fmt',part_updays='$currentDay') successfully!")
          sqlContext.sql(s"insert into hive_org_tdapp_tlaunchnew partition (part_daytime='$days_fmt',part_updays='$currentDay') select * from spark_hive_org_tdapp_tlaunchnew htempa where htempa.daytime='$days'")
          println(s"insert into hive_org_tdapp_tlaunchnew partition (part_daytime='$days_fmt',part_updays='$currentDay') successfully!")
          result += element+1
        }
        result.toList
      }
    }
  }


  /**
    * hive-job-63 2016-11-22
    * org_tdapp_device to hive_org_tdapp_device
    *
    * @author Xue
    * @param sqlContext
    */

  // Xue create function about partition by date ^_^
  def JOB_HV_63(implicit sqlContext: HiveContext,start_dt: String, end_dt: String)  {

    var sdf: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd")
    val start = LocalDate.parse(start_dt, dateFormatter)
    val end = LocalDate.parse(end_dt, dateFormatter)
    val days = Days.daysBetween(start, end).getDays
    val dateStrs = for (day <- 0 to days) {
      val currentDay = (start.plusDays(day).toString(dateFormatter))
      println("######JOB_HV_63######")
      val df = sqlContext.read.parquet(s"$up_namenode/$up_hivedataroot/incident/td/hive_org_tdapp_device/part_updays=$currentDay")
      println(s"###### read $up_namenode/ successful ######")
      df.registerTempTable("spark_hive_org_tdapp_device")

      val daytime:DataFrame = sqlContext.sql(
        s"""
           |select distinct
           |daytime
           |from spark_hive_org_tdapp_device
            """.stripMargin
      )
      daytime.show(10)

      val time_lsit = daytime.select("daytime").rdd.map(r => r(0).asInstanceOf[String]).collect().toList

      increase_ListBuffer(time_lsit)

      def increase_ListBuffer(list:List[String]) :List[String]={
        import scala.collection.mutable.ListBuffer
        var result = ListBuffer[String]()
        for(element <- list){
          val dt = DateTime.parse(element,dateFormat_2)
          val days = element
          val days_fmt = dateFormatter.print(dt)
          sqlContext.sql(s"use $hive_dbname")
          sqlContext.sql(s"alter table hive_org_tdapp_device drop partition (part_daytime='$days_fmt',part_updays='$currentDay')")
          println(s"alter table hive_org_tdapp_device drop partition (part_daytime='$days_fmt',part_updays='$currentDay') successfully!")
          sqlContext.sql(s"insert into hive_org_tdapp_device partition (part_daytime='$days_fmt',part_updays='$currentDay') select * from spark_hive_org_tdapp_device htempa where htempa.daytime='$days'")
          println(s"insert into hive_org_tdapp_device partition (part_daytime='$days_fmt',part_updays='$currentDay') successfully!")
          result += element+1
        }
        result.toList
      }
    }
  }


  /**
    * hive-job-64 2016-11-25
    * org_tdapp_exception to hive_org_tdapp_exception
    *
    * @author Xue
    * @param sqlContext
    */

  // Xue create function about partition by date ^_^
  def JOB_HV_64(implicit sqlContext: HiveContext,start_dt: String, end_dt: String)  {

    var sdf: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd")
    val start = LocalDate.parse(start_dt, dateFormatter)
    val end = LocalDate.parse(end_dt, dateFormatter)
    val days = Days.daysBetween(start, end).getDays
    val dateStrs = for (day <- 0 to days) {
      val currentDay = (start.plusDays(day).toString(dateFormatter))
      println("######JOB_HV_64######")
      val df = sqlContext.read.parquet(s"$up_namenode/$up_hivedataroot/incident/td/hive_org_tdapp_exception/part_updays=$currentDay")
      println(s"###### read $up_namenode/ successful ######")
      df.registerTempTable("spark_hive_org_tdapp_exception")

      val daytime:DataFrame = sqlContext.sql(
        s"""
           |select distinct
           |daytime
           |from spark_hive_org_tdapp_exception
            """.stripMargin
      )
      daytime.show(10)

      val time_lsit = daytime.select("daytime").rdd.map(r => r(0).asInstanceOf[String]).collect().toList

      increase_ListBuffer(time_lsit)

      def increase_ListBuffer(list:List[String]) :List[String]={
        import scala.collection.mutable.ListBuffer
        var result = ListBuffer[String]()
        for(element <- list){
          val dt = DateTime.parse(element,dateFormat_2)
          val days = element
          val days_fmt = dateFormatter.print(dt)
          sqlContext.sql(s"use $hive_dbname")
          sqlContext.sql(s"alter table hive_org_tdapp_exception drop partition (part_daytime='$days_fmt',part_updays='$currentDay')")
          println(s"alter table hive_org_tdapp_exception drop partition (part_daytime='$days_fmt',part_updays='$currentDay') successfully!")
          sqlContext.sql(s"insert into hive_org_tdapp_exception partition (part_daytime='$days_fmt',part_updays='$currentDay') select * from spark_hive_org_tdapp_exception htempa where htempa.daytime='$days'")
          println(s"insert into hive_org_tdapp_exception partition (part_daytime='$days_fmt',part_updays='$currentDay') successfully!")
          result += element+1
        }
        result.toList
      }
    }
  }


  /**
    * hive-job-65 2016-11-25
    * org_tdapp_newuser to hive_org_tdapp_newuser
    *
    * @author Xue
    * @param sqlContext
    */
  // Xue create function about partition by date ^_^
  def JOB_HV_65(implicit sqlContext: HiveContext,start_dt: String, end_dt: String)  {

    var sdf: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd")
    val start = LocalDate.parse(start_dt, dateFormatter)
    val end = LocalDate.parse(end_dt, dateFormatter)
    val days = Days.daysBetween(start, end).getDays
    val dateStrs = for (day <- 0 to days) {
      val currentDay = (start.plusDays(day).toString(dateFormatter))
      println("######JOB_HV_65######")
      val df = sqlContext.read.parquet(s"$up_namenode/$up_hivedataroot/incident/td/hive_org_tdapp_newuser/part_updays=$currentDay")
      println(s"###### read $up_namenode/ successful ######")
      df.registerTempTable("spark_hive_org_tdapp_newuser")

      val daytime:DataFrame = sqlContext.sql(
        s"""
           |select distinct
           |daytime
           |from spark_hive_org_tdapp_newuser
            """.stripMargin
      )
      daytime.show(10)

      val time_lsit = daytime.select("daytime").rdd.map(r => r(0).asInstanceOf[String]).collect().toList

      increase_ListBuffer(time_lsit)

      def increase_ListBuffer(list:List[String]) :List[String]={
        import scala.collection.mutable.ListBuffer
        var result = ListBuffer[String]()
        for(element <- list){
          val dt = DateTime.parse(element,dateFormat_2)
          val days = element
          val days_fmt = dateFormatter.print(dt)
          sqlContext.sql(s"use $hive_dbname")
          sqlContext.sql(s"alter table hive_org_tdapp_newuser drop partition (part_daytime='$days_fmt',part_updays='$currentDay')")
          println(s"alter table hive_org_tdapp_newuser drop partition (part_daytime='$days_fmt',part_updays='$currentDay') successfully!")
          sqlContext.sql(s"insert into hive_org_tdapp_newuser partition (part_daytime='$days_fmt',part_updays='$currentDay') select * from spark_hive_org_tdapp_newuser htempa where htempa.daytime='$days'")
          println(s"insert into hive_org_tdapp_newuser partition (part_daytime='$days_fmt',part_updays='$currentDay') successfully!")
          result += element+1
        }
        result.toList
      }
    }
  }


  /**
    * hive-job-71 2016-11-28
    * hive_ach_order_inf -> hbkdb.rtdtrs_dtl_ach_order_inf (测试环境只有这一天：20151109 有数据)
    *
    * 测试时间段为:
    * start_dt="2015-11-09"
    * end_dt="2015-11-09"
    *
    * @author tzq
    * @param sqlContext
    * @param end_dt
    * @param start_dt
    * @param interval
    */
  def JOB_HV_71(implicit sqlContext: HiveContext,start_dt: String,end_dt:String) = {
    println("######JOB_HV_71######")
    val interval=DateUtils.getIntervalDays(start_dt,end_dt).toInt
    var part_dt = start_dt
    sqlContext.sql(s"use $hive_dbname")

    if(interval>=0){
      for(i <- 0 to interval){
        val temp=(part_dt.toString()).replace("-","")//转为yyMMdd
        val df = sqlContext.read.parquet(s"$up_namenode/$up_hivedataroot/incident/order/hive_ach_order_inf/hp_trans_dt=$temp")
        println(s"###### read $up_namenode/ at partition= $temp successful ######")
        df.registerTempTable("spark_hive_ach_order_inf")

        sqlContext.sql(s"use $hive_dbname")
        sqlContext.sql(s"alter table hive_ach_order_inf drop partition(part_hp_trans_dt='$part_dt')")
        sqlContext.sql(
          s"""
             |insert into table hive_ach_order_inf partition(part_hp_trans_dt='$part_dt')
             |select * from spark_hive_ach_order_inf
       """.stripMargin)
        println(s"#### insert into table(hive_ach_order_inf) at partition= $part_dt successful ####")


        part_dt=DateUtils.addOneDay(part_dt)//yyyy-MM-dd
      }
    }
  }

}
