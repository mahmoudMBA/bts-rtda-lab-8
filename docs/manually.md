- Live stream meetup data from 

[https://www.meetup.com/meetup_api/docs/stream/2/rsvps/#websockets](https://www.meetup.com/meetup_api/docs/stream/2/rsvps/#websockets)

- Download ```liesner/bts-rtda-lab8``` repo, move to repo folder and do   

To increase virtual memory

```bash
sysctl -w vm.max_map_count=262144 
```

up all containers on docker-compose cluster

```
docker-compose up 
```
 
- Connect to ```sensor``` container and  push data from meetup API to kafka topic stream


```bash
docker exec -it sensor /bin/sh

curl -i http://stream.meetup.com/2/rsvps | kafkacat -b kafka:9092 -t stream 
```

- Check kafka is receiving message on the topic stream by runing in a new shell

```bash
docker exec -it sensor /bin/sh
kafkacat -b kafka:9092 -t stream
```


- Go inside spark container and run spark-shell connected with kafka

```bash
docker exec -it spark bash
spark-shell --packages org.apache.spark:spark-sql-kafka-0-10_2.11:2.4.0,org.apache.kafka:kafka-clients:2.2.0,org.apache.spark:spark-tags_2.11:2.4.0,org.apache.spark:spark-sql_2.11:2.4.0,org.elasticsearch:elasticsearch-spark-20_2.11:7.1.1 --conf 'spark.es.nodes=elasticsearch' --conf 'spark.es.port=9200' --conf 'spark.es.nodes.wan.only=true' --conf 'spark.es.index.auto.create=true'

```

- Check  

```bash
val dstream = spark.readStream.format("kafka").option("kafka.bootstrap.servers", "kafka:9092").option("subscribe", "stream").load().selectExpr("CAST(value AS STRING)").writeStream.format("console").start()
```


- Stop previous operation and prepare to read and process meetup data from kafka topic "stream" and write it to elastic index "kafka/es_spark". 

```bash
import java.sql.Timestamp
case class VenueModel(venue_name: Option[String], lon: Option[Double], lat: Option[Double], venue_id: Option[String])
case class MemberModel(member_id: Long, photo: Option[String], member_name: Option[String])
case class Event(event_name: String, event_id: String, time: Long, event_url: Option[String])
case class GTopicModel(urlkey: String, topic_name: String)
case class GroupModel(group_topics: Array[GTopicModel], group_city: String, group_country: String, group_id: Long, group_name: String, group_lon: Double, group_urlname: String, group_state: Option[String], group_lat: Double)
case class MeetupModel(venue: VenueModel, visibility: String, response: String, guests: Long, member: MemberModel, rsvp_id: Long,  mtime: Long, group: GroupModel)


val dstream = spark.readStream.format("kafka").option("kafka.bootstrap.servers", "kafka:9092").option("subscribe", "stream").load().selectExpr("CAST(value AS STRING)").as[String]

import org.json4s._
import org.json4s.jackson.JsonMethods._
val dsMeetups = dstream.map(r=> { implicit val formats = DefaultFormats; parse(r).extract[MeetupModel] } )
val query = dsMeetups.flatMap(meetup=>meetup.group.group_topics)

import org.apache.spark.sql.streaming.{OutputMode}
query.writeStream.format("org.elasticsearch.spark.sql").outputMode(OutputMode.Append()).option("checkpointLocation", "/tmp").option("es.resource", "kafka/es_spark").option("es.nodes", "elasticsearch:9200").option("es.spark.sql.streaming.sink.log.enabled", "false").start()
```
