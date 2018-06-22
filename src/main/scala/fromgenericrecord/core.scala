package fromgenericrecord

import java.util.ArrayList
import org.apache.avro.util.Utf8
import org.apache.avro.generic.GenericRecord
import scala.language.implicitConversions
import scala.collection.JavaConverters._
import scala.reflect.ClassTag
import shapeless._
import shapeless.labelled._


trait FromGenericRecord[A] {
  type DecodingFailure = String

  type Result[A] = Either[DecodingFailure, A]
  def decode(record: GenericRecord): Result[A]
}

abstract class Read[T] extends (Any => Either[String, T])
object Read {
  def apply[T](implicit inst: Read[T]): Read[T] = inst

  def fromTypeable[T](implicit typeable: Typeable[T]): Read[T] = new Read[T] {
    def apply(any: Any): Either[String, T] = {
      typeable.cast(any).toRight(
        s"Wrong data type ${any.getClass}; expected ${typeable.describe}"
      )
    }
  }

  implicit val bool: Read[Boolean] = fromTypeable
  implicit val int: Read[Integer] = fromTypeable
  implicit val long: Read[Long] = fromTypeable
  implicit val float: Read[Float] = fromTypeable
  implicit val double: Read[Double] = fromTypeable
  implicit val utf8: Read[Utf8] = fromTypeable

  implicit val byteArray: Read[Array[Byte]] = new Read[Array[Byte]] {
    def apply(any: Any): Either[String, Array[Byte]] = {
      any match {
        case v: Array[Byte] => Right(v)
        case _ => Left(s"Expected array of bytes")
      }
    }
  }

  // Convert Utf8 to String
  implicit val string: Read[String] = new Read[String] {
    def apply(any: Any): Either[String, String] = {
      any match {
        case s: String => Right(s)
        case u: Utf8 => Right(u.toString())
        case z => Left(s"Expected string or Utf8, got ${z.getClass}")
      }
    }
  }

  implicit def genericRecord[T](implicit v: FromGenericRecord[T]): Read[T] = new Read[T] {
    def apply(any: Any): Either[String, T] = {
      any match {
        case r: GenericRecord => v.decode(r)
        case s => Left(s"Expected GenericRecord got ${s.getClass}")
      }
    }
  }

  implicit def array[T: ClassTag](implicit v: Read[T]): Read[Seq[T]] = new Read[Seq[T]] {
    def apply(any: Any): Either[String, Seq[T]] = {
      any match {
        case a: Array[_] => sequence(a.map(i => v(i)))
        case s => Left(s"Expected array, got ${s.getClass}")
      }
    }
  }

  //helper for array
  private def sequence[A, B: ClassTag](s:  Seq[Either[A,B]]): Either[A, Seq[B]] = {
    val seq: Either[A, Seq[B]] = s.foldRight(Right(Nil): Either[A, List[B]]) {
      (e,acc) => for (xs <- acc.right; x<- e.right) yield x::xs
    }
    seq
  }

}

abstract class ReadField[T] extends ((String, GenericRecord) => Either[String, T])
object ReadField {
  def apply[T](implicit inst: ReadField[T]): ReadField[T] = inst

  implicit def required[T](implicit r: Read[T]): ReadField[T] = new ReadField[T] {
    def apply(key: String, record: GenericRecord): Either[String, T] = {
      Option(record.get(key)).toRight(s"{key} not found in record").flatMap(v => r(v)).left.map(e => s"Error decoding ${key}: $e")
    }
  }

  implicit def optionalField[T](implicit v: Read[T]): ReadField[Option[T]] = new ReadField[Option[T]] {
    def apply(key: String, record: GenericRecord): Either[String, Option[T]] = {
      Option(record.get(key)) match {
        case Some(i) => v(i).map(i2 => Some(i2)).left.map(e => s"Error decoding key ${key}: $e")
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

