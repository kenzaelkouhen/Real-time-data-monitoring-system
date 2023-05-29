import scala.util.Random

object RandomGenerator {

  val random = new Random(23)

  def randomHourInMs(min: Int, max: Int, fdv: Int): Int = random.between(min,max)*3600*1000 / fdv

  def sample[T](collection: Iterable[T]): T = collection.toList(random.between(0, collection.size))

  def sample[T](collection: Iterable[T], size: Int): Iterable[T] = random.shuffle(collection).take(size)

  def sample[T](collection: Iterable[T], omit: T): T = random.shuffle(collection.filterNot(_==omit)).head
}
