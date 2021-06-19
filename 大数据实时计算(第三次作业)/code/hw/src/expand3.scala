import java.util.Properties
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import java.sql.{Connection, DriverManager, ResultSet}
import scala.collection.mutable.ArrayBuffer

/*
* 将Mysql中的数据存入Kafka队列
*/

object expand3 {
  //Mysql参数
  val url = "jdbc:mysql://localhost:3306/liuyixin"
  //驱动名称
  val driver = "com.mysql.jdbc.Driver"
  //用户名
  val username = "root"
  //密码
  val password = "zxczxcz123"

  //kafka参数
  val topic = "liuyixin"
  val bootstrapServers = "bigdata35.depts.bingosoft.net:29035,bigdata36.depts.bingosoft.net:29036,bigdata37.depts.bingosoft.net:29037"

  def main(args: Array[String]): Unit = {
    val mysql_data = readData()
    //    mysql_data.foreach(println(_))
    produceToKafka(mysql_data)
  }

  /**
   * 从mysql中读取文件内容
   *
   * @return mysql的文件内容
   */
  def readData(): ArrayBuffer[String] = {
    var connection:Connection = null
    var result=new ArrayBuffer[String]
    try{
      //创建连接
      classOf[com.mysql.jdbc.Driver]
      connection = DriverManager.getConnection(url,username,password)
      val statement = connection.createStatement()
      val resultSet=statement.executeQuery("SELECT number,name,age from student")
      while (resultSet.next()){
        val number = resultSet.getString("number")
        val name = resultSet.getString("name")
        val age=resultSet.getString("age")
        result.append(s"$number,$name,$age")
      }
      result
    } catch {
      case e:Exception => e.printStackTrace()
        result
    }finally {
      if(connection == null){
        connection.close()
      }
    }

  }

  /**
   * 把数据写入到kafka中
   *
   * @param mysql_data 要写入的内容
   */
  def produceToKafka(mysql_data: ArrayBuffer[String]): Unit = {
    val props = new Properties
    props.put("bootstrap.servers", bootstrapServers)
    props.put("acks", "all")
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
    val producer = new KafkaProducer[String, String](props)
    for (data <- mysql_data) {
      if (!data.trim.isEmpty) {
        val record = new ProducerRecord[String, String](topic, null, data)
        println("开始生产数据：" + data)
        producer.send(record)
      }
    }
    producer.flush()
    producer.close()
  }
}