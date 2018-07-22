# What is this

A very small library using Shapeless to map Apache Avro `GenericRecord`s to Scala case classes. This might be useful in production but it's mainly an excuse for me to do something non-trivial with Shapeless.

## The Problem

You have data serialized with a schemaregistry - typically in a kafka topic - and you're reading data using something like the following:

```scala
import collection.JavaConverters._
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde

val config: Properties = {
  val p = new Properties()
  p.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, sys.env("KAFKA_SERVER"))
  p.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, classOf[Serdes.ByteArraySerde])
  p.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, classOf[GenericAvroSerde])
  p.put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, sys.env("SCHEMA_REGISTRY_URL"))
  p
}

val consumer = new KafkaConsumer[ByteArray, GenericRecord](props)

consumer.subscribe(List("sometopic").asJava)

while(true) {
    val records = consumer.poll(100)
    for (record <- records) {
        //Do something with the GenericRecord
    }
}

```

The problem is that `GenericRecord` is a really combersome API. You end up writing a lot of boilerplate to go from `GenericRecord` to your internal data representation.

For example, say we have a topic publishing messages with a users state and we want to convert theses messages to a case class called `User`.

```scala

case class UserStateEvent(id: UUID, name: String, email: String)

//Generically get some kind of data from a GenericRecord
def get[A: ClassTag](key: String, data: GenericRecord): Either[String, A] = {
  val result: Option[Any] = Option(data.get(key))
  result match {
    case Some(someval) => someval match {
      case a: A => Right(a)
      case otherwise: Any => Left(s"Unexpectedly got ${otherwise.getClass()} for key $key")
    }
    case None => Left(s"No value for key $key")
  }
}

def getString(key: String, data: GenericRecord): Either[String, String] = {
  return get[Utf8](key, data).map(_.toString)
}

def userFromRecord(record: GenericRecord): Either[String, User] = {
  for {
    userId <- getString("id", event).right.map((d: String) => UUID(d))
    name <- getString("name", event).right
  } yield UserCreated(userId, name, email)
}
```

This quickly becomes lots of boilerplate as you add events and it's even more complicated once you add hierarchies of case classes.

## The Solution

```scala
import fromgenericrecord._

case class UserStateEvent(id: UUID, name: String, email: String)

def userFromRecord(record: GenericRecord): Either[String, UserStateEvent] = {
    FromGenericRecord[UserStateEvent].decode(record)
}
```


