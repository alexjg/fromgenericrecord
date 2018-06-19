package fromgenericrecord

import org.apache.avro.generic.GenericRecord
import shapeless._
import shapeless.labelled._


trait FromGenericRecord[A] {
  type DecodingFailure = String

  type Result[A] = Either[DecodingFailure, A]
  def decode(record: GenericRecord): Result[A]
}

abstract class ReadField[T] extends ((String, GenericRecord) => Either[String, T])
object ReadField {
  def apply[T](implicit inst: ReadField[T]): ReadField[T] = inst
  def required[T](fn: (String, Any) => Either[String, T]): ReadField[T] = new ReadField[T] {
    def apply(key: String, record: GenericRecord): Either[String, T] = {
      Option(record.get(key)).toRight(s"{key} not found in record").flatMap(v => fn(key, v))
    }
  }

  def fromTypeable[T](implicit typeable: Typeable[T]): ReadField[T] = required {
    (key, any) => {
      typeable.cast(any).toRight(
        s"Wrong data type ${any.getClass} for key $key; expected ${typeable.describe}"
      )
    }
  }

  implicit val string: ReadField[String] = fromTypeable
  implicit val bool: ReadField[Boolean] = fromTypeable
  implicit val int: ReadField[Integer] = fromTypeable
  implicit val long: ReadField[Long] = fromTypeable
  implicit val float: ReadField[Float] = fromTypeable
  implicit val double: ReadField[Double] = fromTypeable

  implicit val byteArray: ReadField[Array[Byte]] = required {
    (key, any) => {
      any match {
        case v: Array[Byte] => Right(v)
        case _ => Left(s"Expected array of bytes for $key")
      }
    }
  }

  implicit def nestedField[T](implicit v: FromGenericRecord[T]): ReadField[T] = new ReadField[T] {
    def apply(key: String, record: GenericRecord): Either[String, T] = {
      val innerRecord = Option(record.get(key)).toRight(s"${key} not found in record")
      innerRecord match {
        case Right(g: GenericRecord) => v.decode(g)
        case s => Left(s"Expected record for ${key} got ${s.getClass}")
      }
    }
  }

  implicit def optionalField[T](implicit v: ReadField[T]): ReadField[Option[T]] = new ReadField[Option[T]] {
    def apply(key: String, record: GenericRecord): Either[String, Option[T]] = {
      Option(record.get(key)) match {
        case Some(i) => v(key, record).map(i2 => Some(i2))
        case None => Right(None)
      }
    }
  }

}

object FromGenericRecord {
  def apply[A](implicit decoder: FromGenericRecord[A]): FromGenericRecord[A] = decoder

  implicit val hnil: FromGenericRecord[HNil] = new FromGenericRecord[HNil] {
    def decode(record: GenericRecord): Either[String, HNil] = Right(HNil)
  }

  implicit  def hcons[K <: Symbol, H, T <: HList](implicit
    key: Witness.Aux[K],
    readH: ReadField[H],
    readT: FromGenericRecord[T]
    ): FromGenericRecord[FieldType[K, H] :: T]  = new FromGenericRecord[FieldType[K, H] :: T] {
      def decode(record: GenericRecord): Either[String, FieldType[K, H] :: T] = {
        for {
          headResult <- readH(key.value.name, record).map(v => field[K](v))
          tailResult <- readT.decode(record)
        } yield (headResult :: tailResult)
      }
    }

  implicit def generic[A, H](implicit
    gen: LabelledGeneric.Aux[A, H],
    readL: FromGenericRecord[H]
  ): FromGenericRecord[A] = new FromGenericRecord[A] {
    def decode(record: GenericRecord): Either[String, A] = readL.decode(record).map(gen.from)
  }

}

