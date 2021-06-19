import java.util
import java.util.{Properties, UUID}
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.node.ObjectNode
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer010
import org.apache.flink.streaming.util.serialization.JSONKeyValueDeserializationSchema

object Main3 {
  val accessKey = "E441571B4C6B777EB1F8"
  val secretKey = "WzY1QTg4N0JBOTBGQUJFOUNDQTZERTlFMDRBMkI1"
  //s3地址
  val endpoint = "http://scut.depts.bingosoft.net:29997"
  //上传到的桶
  val bucket = "liuyixin"
  //上传文件的路径前缀
  val keyPrefix = "upload/"
  //上传数据间隔 单位毫秒
  val period = 5000
  //需要监控的人名
  val place = "济南市"
  val inputTopics: util.ArrayList[String] = new util.ArrayList[String]() {
    {
      add("zzy_buy_ticket_1") //车票购买记录主题
    }
  }
  val bootstrapServers = "bigdata35.depts.bingosoft.net:29035,bigdata36.depts.bingosoft.net:29036,bigdata37.depts.bingosoft.net:29037"

  def main(args: Array[String]): Unit = {
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setParallelism(1)
    val kafkaProperties = new Properties()
    kafkaProperties.put("bootstrap.servers", bootstrapServers)
    kafkaProperties.put("group.id", UUID.randomUUID().toString)
    kafkaProperties.put("auto.offset.reset", "earliest")
    kafkaProperties.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
    kafkaProperties.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
    val kafkaConsumer = new FlinkKafkaConsumer010[ObjectNode](inputTopics,
      new JSONKeyValueDeserializationSchema(true), kafkaProperties)
    kafkaConsumer.setCommitOffsetsOnCheckpoints(true)
    val inputKafkaStream = env.addSource(kafkaConsumer)
    inputKafkaStream.filter(x => x.get("value").get("origin").asText("").equals(place)).map(x => println(x))
    val mydata= inputKafkaStream.filter(x => x.get("value").get("origin").asText("").equals(place)).map(x => x.toString)
    mydata.writeUsingOutputFormat(new S3Writer(accessKey, secretKey, endpoint, bucket, keyPrefix, period))
    env.execute()
  }
}
