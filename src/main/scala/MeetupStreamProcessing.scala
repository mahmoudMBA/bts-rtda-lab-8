import models.{GTopicModel, MeetupModel, MemberName, VenueNameAndLocation, EventTopicCount}
import org.apache.spark.sql.{Dataset, SparkSession}
import org.json4s._
import org.json4s.jackson.JsonMethods._

class MeetupStreamProcessing (spark: SparkSession) {

  //Method to connect to kafka topic stream and produce a Dataset[String] taking the value field.
  def connectToKafkaStreamAndGetStringDatasetFromValue(kafkaHost: String): Dataset[String] = {
    import spark.implicits._
    val kafkaStreamStringDataset = spark
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", kafkaHost + ":9092")
      .option("subscribe", "stream")
      .load()
      .selectExpr("CAST(value AS STRING)").as[String]
    return kafkaStreamStringDataset
  }

  //Transformation method
  def transformFromStringDatasetToMeetupmodelDataset(kafkaStreamStringDataset: Dataset[String]): Dataset[MeetupModel] = {
    import spark.implicits._
    val dsMeetups = kafkaStreamStringDataset
      .map(r=> { implicit val formats = DefaultFormats; parse(r).extract[MeetupModel] } )
    return dsMeetups
  }


  //Processing methods
  def extractMeetupTopics(meetupStreamDataset: Dataset[MeetupModel]): Dataset[GTopicModel] = {
    import spark.implicits._
    return meetupStreamDataset
      .flatMap(meetup => meetup.group.group_topics )
      .as[GTopicModel]
  }

  //To be implemented
  def extractVenueNameAndLocation(meetupStreamDataset: Dataset[MeetupModel]): Dataset[VenueNameAndLocation] = {
    import spark.implicits._
    return meetupStreamDataset.map(meetup =>
      VenueNameAndLocation(meetup.venue.venue_name.getOrElse(""),
      meetup.venue.lon.getOrElse(0.0).toString + "," + meetup.venue.lat.getOrElse().toString))
        .as[VenueNameAndLocation]
  }

  //To be implemented
  def extractMemberName(meetupStreamDataset: Dataset[MeetupModel]): Dataset[MemberName] = {
    import spark.implicits._
    return meetupStreamDataset.map(meetup => meetup.member.member_name)
      .as[MemberName]
  }

  def extractEventTopicCount(meetupStreamDataset: Dataset[MeetupModel]): Dataset[EventTopicCount] = {
    import spark.implicits._
    return meetupStreamDataset.map(meetup => EventTopicCount(meetup.venue.venue_name.getOrElse(""), meetup.group.group_topics.length))
      .as[EventTopicCount]
  }


}
