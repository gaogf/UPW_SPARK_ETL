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

  def main(args: Array[String]): Unit = {
    val conf = new SparkConf().setAppName("SparkUPH2H")
    val sc = new SparkContext(conf)
    sc.setLogLevel("ERROR")
    implicit val sqlContext = new HiveContext(sc)

    val rowParams=UPSQL_TIMEPARAMS_JDBC.readTimeParams(sqlContext)
    val start_dt=DateUtils.getYesterdayByJob(rowParams.getString(0))//获取开始日期：start_dt-1
    val end_dt=rowParams.getString(1)//结束日期(未减一处理)

    println(s"####当前JOB的执行日期为：$end_dt####")

    JOB_HV_50(sqlContext,"2016-04-13","2016-04-13")

//    val jobName = if(args.length>0) args(0) else None
//    println(s"#### 当前执行JobName为： $jobName ####")
//    jobName match {
//      /**
//        * 每日模板job
//        */
//      case "JOB_HV_39"  => JOB_HV_39(sqlContext,end_dt) //CODE BY YX
//      case "JOB_HV_41"  => JOB_HV_41(sqlContext,start_dt,end_dt) //CODE BY YX
//      case "JOB_HV_49"  => JOB_HV_49 //CODE BY YX
//      case "JOB_HV_52"  => JOB_HV_52(sqlContext,end_dt) //CODE BY YX
//
//      /**
//        * 指标套表job
//        */
//
//    }

    sc.stop()
  }

  /**
    * hive-job-39 2016-08-30
    * rtdtrs_dtl_achis to hive_achis_trans
    * @author winslow yang
    * @param sqlContext
    */
  def JOB_HV_39(implicit sqlContext: HiveContext,end_dt:String) = {

    println("######JOB_HV_39######")

    val today_dt = end_dt
    val df = sqlContext.read.parquet(s"$up_namenode/$up_hivedataroot/hive_achis_trans/part_settle_dt=$today_dt")
    println(s"###### read $up_namenode/ successful ######")
    df.registerTempTable("spark_hive_achis_trans")

    sqlContext.sql(s"use $hive_dbname")
    sqlContext.sql(s"alter table hive_achis_trans drop partition (part_settle_dt='$today_dt')")
    sqlContext.sql(s"alter table hive_achis_trans add partition (part_settle_dt='$today_dt')")
    sqlContext.sql(s"insert into table hive_achis_trans partition(part_settle_dt='$today_dt') select * from spark_hive_achis_trans")
    println("#### insert into table success ####")

  }


  /**
    * hive-job-41 2016-11-03
    * rtdtrs_dtl_cups to hive_cups_trans
    * @author Xue
    * @param sqlContext
    */

    // Xue create function about partition by date ^_^
    def JOB_HV_41(implicit sqlContext: HiveContext,start_dt: String, end_dt: String)  {
      var sdf: SimpleDateFormat = new SimpleDateFormat("yyyy-MM-dd")
      val start = LocalDate.parse(start_dt, dateFormatter)
      val end = LocalDate.parse(end_dt, dateFormatter)
      val days = Days.daysBetween(start, end).getDays
      val dateStrs = for (day <- 0 to days) {
        val currentDay = (start.plusDays(day).toString(dateFormatter))
        val df = sqlContext.read.parquet(s"$up_namenode/$up_hivedataroot/hive_cups_trans/part_settle_dt=$currentDay")
        println(s"###### read $up_namenode/ successful ######")
        df.registerTempTable("spark_hive_cups_trans")

        println(s"=========插入'$currentDay'分区的数据=========")
        sqlContext.sql(s"use $hive_dbname")
        sqlContext.sql(s"alter table hive_cups_trans drop partition (part_settle_dt='$currentDay')")
        println(s"alter table hive_cups_trans drop partition (part_settle_dt='$currentDay') successfully!")
        sqlContext.sql(s"insert into hive_cups_trans partition (part_settle_dt='$currentDay') select * from spark_hive_cups_trans htempa where htempa.part_settle_dt = '$currentDay'")
        println(s"insert into hive_cups_trans partition (part_settle_dt='$currentDay') successfully!")
      }
    }





  /**
    * hive-job-49 2016-09-14
    * rtapam_prv_ucbiz_cdhd_bas_inf to hive_ucbiz_cdhd_bas_inf
    * @author winslow yang
    * @param sqlContext
    */
  def JOB_HV_49(implicit sqlContext: HiveContext) = {

    println("######JOB_HV_49######")

    val df = sqlContext.read.parquet(s"$up_namenode/$up_hivedataroot/hive_ucbiz_cdhd_bas_inf")
    println(s"###### read $up_namenode successful ######")
    df.registerTempTable("spark_ucbiz_cdhd_bas_inf")

    sqlContext.sql(s"use $hive_dbname")
    sqlContext.sql("truncate table hive_ucbiz_cdhd_bas_inf")
    sqlContext.sql("insert into table hive_ucbiz_cdhd_bas_inf select * from spark_ucbiz_cdhd_bas_inf")
    println("#### insert into table (hive_ucbiz_cdhd_bas_inf) success ####")

  }

  /**
    * hive-job-50 2016-11-03
    * org_tdapp_keyvalue to hive_org_tdapp_keyvalue
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
        val df = sqlContext.read.parquet(s"$up_namenode/$up_hivedataroot/hive_org_tdapp_keyvalue/part_updays=$currentDay")
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
            val fmt1 = DateTimeFormat.forPattern("yyyyMMdd")
            val fmt2 = DateTimeFormat.forPattern("yyyy-MM-dd")
            val dt = DateTime.parse(element,fmt1)
            val days = fmt2.print(dt)
            sqlContext.sql(s"use $hive_dbname")
            sqlContext.sql(s"alter table hive_org_tdapp_keyvalue drop partition (part_daytime='$days',part_updays='$currentDay')")
            println(s"alter table hive_org_tdapp_keyvalue drop partition (part_daytime='$days',part_updays='$currentDay') successfully!")
            sqlContext.sql(s"insert into hive_org_tdapp_keyvalue partition (part_daytime='$days',part_updays='$currentDay') select * from spark_hive_org_tdapp_keyvalue htempa where htempa.daytime='$dt'")
            println(s"insert into hive_org_tdapp_keyvalue partition (part_daytime='$days',part_updays='$currentDay') successfully!")
            result += element+1
          }
          result.toList
        }
      }
    }


  /**
    * hive-job-52 2016-08-29
    * stmtrs_bsl_active_card_acq_branch_mon1 to hive_active_card_acq_branch_mon
    * @author winslow yang
    * @param sqlContext
    */
  def JOB_HV_52(implicit sqlContext: HiveContext,end_dt:String) = {

    println("######JOB_HV_52######")
    val part_dt = end_dt.substring(0,7)
    val df = sqlContext.read.parquet(s"$up_namenode/$up_hivedataroot/hive_active_card_acq_branch_mon/part_settle_month=$part_dt")
    println(s"###### read $up_namenode/ successful ######")
    df.registerTempTable("spark_active_card_acq_branch_mon")

    sqlContext.sql(s"use $hive_dbname")
    sqlContext.sql(s"alter table hive_active_card_acq_branch_mon drop partition(part_settle_month='$part_dt')")
    sqlContext.sql(
      s"""
         |insert into table hive_active_card_acq_branch_mon partition(part_settle_month='$part_dt')
         |select * from spark_active_card_acq_branch_mon
       """.stripMargin)
    println("#### insert into table(hive_active_card_acq_branch_mon) success ####")

  }


  /**
    * hive-job-71 2016-11-2
    * hive_ach_order_inf -> rtdtrs_dtl_ach_order_inf
    * @author tzq
    * @param sqlContext
    * @param end_dt
    */
  def JOB_HV_71(implicit sqlContext: HiveContext,end_dt:String) = {

    println("######JOB_HV_71######")
    val part_dt = end_dt.substring(0,7)
    val df = sqlContext.read.parquet(s"$up_namenode/$up_hivedataroot/hive_ach_order_inf/hp_trans_dt=$part_dt")
    println(s"###### read $up_namenode/ successful ######")
    df.registerTempTable("spark_hive_ach_order_inf")

    sqlContext.sql(s"use $hive_dbname")
    sqlContext.sql(s"alter table hive_ach_order_inf drop partition(hp_trans_dt='$part_dt')")
    sqlContext.sql(
      s"""
         |insert into table hive_ach_order_inf partition(hp_trans_dt='$part_dt')
         |select * from spark_hive_ach_order_inf
       """.stripMargin)
    println("#### insert into table(hive_ach_order_inf) success ####")

  }

}
