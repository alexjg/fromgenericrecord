package fromgenericrecord

import org.scalacheck.Properties
import org.scalacheck.Prop.forAll
import org.apache.avro.SchemaBuilder
import org.apache.avro.Schema
import org.apache.avro.util.Utf8
import org.apache.avro.generic.{GenericRecord, GenericData}
import shapeless._
import org.scalacheck.{Gen, Arbitrary}
import Gen._
import Arbitrary.arbitrary

object FromGenericRecordSpecification extends Properties("FromGenericRecord") {

  case class SimpleRecordTest(
    aString: String,
    aBoolean: Boolean,
    anInt: Integer,
    aLong: Long,
    aFloat: Float,
    aDouble: Double,
    someBytes: Array[Byte]
  )

  lazy val simpleSchema: Schema = {
    SchemaBuilder
      .record("SimpleRecord")
      .fields()
        .name("aString").`type`().stringType().noDefault()
        .name("aBoolean").`type`().booleanType().noDefault()
        .name("anInt").`type`().intType().noDefault()
        .name("aLong").`type`().longType().noDefault()
        .name("aFloat").`type`().floatType().noDefault()
        .name("aDouble").`type`().doubleType().noDefault()
        .name("someBytes").`type`().bytesType().noDefault()
      .endRecord()
  }

  implicit def arbSimpleRecordTest: Arbitrary[SimpleRecordTest] = Arbitrary {
    for {
      s <- arbitrary[String]
      b <- arbitrary[Boolean]
      i <- arbitrary[Int]
      l <- arbitrary[Long]
      f <- arbitrary[Float]
      d <- arbitrary[Double]
      ba <- arbitrary[Array[Byte]]
    } yield SimpleRecordTest(s, b, i, l, f, d, ba)
  }


  def encodeSimpleRecordTest(r: SimpleRecordTest): GenericRecord = {
    val rec = new GenericData.Record(simpleSchema)
    rec.put("aString", r.aString)
    rec.put("aBoolean", r.aBoolean)
    rec.put("anInt", r.anInt)
    rec.put("aLong", r.aLong)
    rec.put("aFloat", r.aFloat)
    rec.put("aDouble", r.aDouble)
    rec.put("someBytes", r.someBytes)
    rec
  }

  property("encoding and decoding should result in the same object") = forAll { (r: SimpleRecordTest) =>
    FromGenericRecord[SimpleRecordTest].decode(encodeSimpleRecordTest(r)) == Right(r)
  }

  case class OptionalCaseTest(anOptionalString: Option[String])

  lazy val optionalCaseSchema: Schema = {
    SchemaBuilder
      .record("OptionalRecord")
      .fields()
        .name("anOptionalString").`type`().optional().stringType()
      .endRecord()
  }

  implicit def arbOptionalCaseTest: Arbitrary[OptionalCaseTest] = Arbitrary {
    for {
      i <- arbitrary[Option[String]]
    } yield OptionalCaseTest(i)
  }

  def encodeOptionalCaseTest(r: OptionalCaseTest): GenericRecord = {
    val rec = new GenericData.Record(optionalCaseSchema)
    r.anOptionalString match {
      case Some(s) => rec.put("anOptionalString", s)
      case None => {}
    }
    rec
  }


  property("encoding and decoding optional types should result in the same object") = forAll { (r: OptionalCaseTest) =>
    FromGenericRecord[OptionalCaseTest].decode(encodeOptionalCaseTest(r)) == Right(r)
  }

  case class InnerRecordTest(field1: String, field2: Integer)
  case class NestedSchemaTest(simpleField: String, complexField: InnerRecordTest)

  lazy val nestedSchema: Schema = {
    SchemaBuilder
      .record("OuterRecord")
        .fields()
          .name("simpleField").`type`().stringType().noDefault()
          .name("complexField").`type`().record("InnerRecord")
            .fields()
              .name("field1").`type`().stringType().noDefault()
              .name("field2").`type`().intType().noDefault()
          .endRecord().noDefault()
      .endRecord()
  }

  implicit def arbNestedSchemaTest: Arbitrary[NestedSchemaTest] = Arbitrary {
    for {
      field1 <- arbitrary[String]
      field2 <- arbitrary[Int]
      simple <- arbitrary[String]
    } yield NestedSchemaTest(simple, InnerRecordTest(field1, field2))
  }

  def encodeNestedSchemaTest(r: NestedSchemaTest): GenericRecord  = {
    val innerSchema = nestedSchema.getField("complexField").schema()
    val innerRecord = new GenericData.Record(innerSchema)
    innerRecord.put("field1", r.complexField.field1)
    innerRecord.put("field2", r.complexField.field2)
    val rec = new GenericData.Record(nestedSchema)
    rec.put("simpleField", r.simpleField)
    rec.put("complexField", innerRecord)
    rec
  }

  property("encoding and decoding nested schemas should result in the same object") = forAll { (r: NestedSchemaTest) =>
    FromGenericRecord[NestedSchemaTest].decode(encodeNestedSchemaTest(r)) == Right(r)
  }

  case class Utf8TestCaseClass(value: String)

  lazy val utf8TestSchema: Schema = {
    SchemaBuilder
      .record("utf8test")
        .fields()
          .name("value").`type`().stringType().noDefault()
      .endRecord()
  }

  def encodeUtf8TestRecord(r: Utf8TestCaseClass): GenericRecord = {
    val rec = new GenericData.Record(utf8TestSchema)
    rec.put("value", new Utf8(r.value))
    rec
  }

  implicit def arbitraryUtf8Test: Arbitrary[Utf8TestCaseClass] = Arbitrary {
    arbitrary[String].flatMap(Utf8TestCaseClass(_))
  }

  property("Utf8 should be decoded to string") = forAll { (r: Utf8TestCaseClass) =>
    FromGenericRecord[Utf8TestCaseClass].decode(encodeUtf8TestRecord(r)) == Right(r)
  }

  case class ArrayTestItem(name: String)
  case class ArrayTestOuter(inner: Seq[ArrayTestItem])

  lazy val arrayTestSchema: Schema = {
    SchemaBuilder
      .record("arraytest")
        .fields()
          .name("inner").`type`().array().items()
            .record("itemSchema").fields().name("name").`type`().stringType().noDefault().endRecord.noDefault()
        .endRecord()
  }

  def encodeArrayTestRecord(r: ArrayTestOuter): GenericRecord = {
    val itemsSchema = arrayTestSchema.getField("inner").schema()
    val itemSchema = itemsSchema.getElementType()
    val items = new GenericData.Array[GenericRecord](r.inner.length, itemsSchema)
    r.inner.foreach { i =>
      val rec = new GenericData.Record(itemSchema)
      rec.put("name", i.name)
      items.add(rec)
    }
    val rec = new GenericData.Record(arrayTestSchema)
    rec.put("inner", items)
    rec
  }

  implicit def arbitraryArrayItem: Arbitrary[ArrayTestOuter] = Arbitrary {
    arbitrary[Array[String]].flatMap(names => ArrayTestOuter(names.map(i => ArrayTestItem(i))))
  }

  property("Arrays should be decoded") = forAll { (r: ArrayTestOuter) =>
    FromGenericRecord[ArrayTestOuter].decode(encodeArrayTestRecord(r)) == Right(r)
  }


}
