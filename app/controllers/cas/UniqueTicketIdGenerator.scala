package controllers.cas

import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

import com.google.inject.Inject
import org.apache.commons.lang3.StringUtils
import org.slf4j.{Logger, LoggerFactory}

/**
 * Created by tash on 12/14/15.
 */
trait UniqueTicketIdGenerator {
  /**
   * Return a new unique ticket id beginning with the prefix.
   *
   * @param prefix The prefix we want attached to the ticket.
   * @return the unique ticket id
   */
  def getNewTicketId(prefix: String): String
}

/**
 * Interface to return a random String.
 *
 * @author Scott Battaglia

 * @since 3.0.0
 */
trait RandomStringGenerator {
  /**
   * @return the minimum length as an int guaranteed by this generator.
   */
  def getMinLength: Int

  /**
   * @return the maximum length as an int guaranteed by this generator.
   */
  def getMaxLength: Int

  /**
   * @return the new random string
   */
  def getNewString: String

  /**
   * Gets the new string as bytes.
   *
   * @return the new random string as bytes
   */
  def getNewStringAsBytes: Array[Byte]
}

/**
 * Interface to return a new sequential number for each call.
 *
 * @author Scott Battaglia

 * @since 3.0.0
 */
trait NumericGenerator {
  /**
   * Method to retrieve the next number as a String.
   *
   * @return the String representation of the next number in the sequence
   */
  def getNextNumberAsString: String

  /**
   * The guaranteed maximum length of a String returned by this generator.
   *
   * @return the maximum length
   */
  def maxLength: Int

  /**
   * The guaranteed minimum length of a String returned by this generator.
   *
   * @return the minimum length.
   */
  def minLength: Int
}

object TicketGrantingTicketUniqueTicketIdGenerator extends UniqueTicketIdGenerator {
  /**
   * Return a new unique ticket id beginning with the prefix.
   *
   * @param prefix The prefix we want attached to the ticket.
   * @return the unique ticket id
   */
  override def getNewTicketId(prefix: String): String = {
    prefix + "#" + UUID.randomUUID().toString
  }
}

/**
 * Default implementation of {@link UniqueTicketIdGenerator}. Implementation
 * utilizes a DefaultLongNumericGeneraor and a DefaultRandomStringGenerator to
 * construct the ticket id.
 * <p>
 * Tickets are of the form [PREFIX]-[SEQUENCE NUMBER]-[RANDOM STRING]-[SUFFIX]
 * </p>
 *
 * @author Scott Battaglia
 * @since 3.0.0
 */
case class DefaultUniqueTicketIdGenerator @Inject() (numericGenerator: NumericGenerator,
               val randomStringGenerator: RandomStringGenerator,  val suffix: String) extends UniqueTicketIdGenerator {
  /** The logger instance. */
  protected final val logger: Logger = LoggerFactory.getLogger(this.getClass)
  /** The numeric generator to generate the static part of the id. */

  final def getNewTicketId(prefix: String): String = {
    val number: String = this.numericGenerator.getNextNumberAsString
    val buffer: StringBuilder = new StringBuilder(prefix.length + 2 +
      (if (StringUtils.isNotBlank(this.suffix)) this.suffix.length else 0)
      + this.randomStringGenerator.getMaxLength + number.length)
    buffer.append(prefix)
    buffer.append('-')
    buffer.append(number)
    buffer.append('-')
    buffer.append(this.randomStringGenerator.getNewString)
    if (this.suffix != null) {
      buffer.append(this.suffix)
    }
    buffer.toString
  }
}

/**
 * Interface to guaranteed to return a long.
 *
 * @author Scott Battaglia

 * @since 3.0.0
 */
trait LongNumericGenerator extends NumericGenerator {
  /**
   * Get the next long in the sequence.
   *
   * @return the next long in the sequence.
   */
  def getNextLong: Long
}

case class DefaultLongNumericGenerator(initialValue: Long) extends LongNumericGenerator {
  /** The maximum length the string can be. */
  val MAX_STRING_LENGTH: Int = java.lang.Long.toString(java.lang.Long.MAX_VALUE).length

  /** The minimum length the String can be. */
  val MIN_STRING_LENGTH: Int = 1

  var count = new AtomicLong(initialValue)

  def getNextLong: Long = {
    return this.getNextValue
  }

  def getNextNumberAsString: String = {
    return java.lang.Long.toString(this.getNextValue)
  }

  def maxLength: Int = {
    MAX_STRING_LENGTH
  }

  def minLength: Int = {
    MIN_STRING_LENGTH
  }

  /**
   * Gets the next value.
   *
   * @return the next value. If the count has reached { @link Long#MAX_VALUE},
   *                                                          then { @link Long#MAX_VALUE} is returned. Otherwise, the next increment.
   */
  protected def getNextValue: Long = {
    if (this.count.compareAndSet(java.lang.Long.MAX_VALUE, 0) ) {
      return java.lang.Long.MAX_VALUE
    }
    return this.count.getAndIncrement
  }
}

/**
 * Implementation of the RandomStringGenerator that allows you to define the
 * length of the random part.
 *
 * @author Scott Battaglia

 * @since 3.0.0
 */

case class DefaultRandomStringGenerator(maxRandomLength: Int) extends RandomStringGenerator {
  /** The default maximum length. */
  protected val DEFAULT_MAX_RANDOM_LENGTH: Int = 35
  /** The array of printable characters to be used in our random string. */
  private val PRINTABLE_CHARACTERS: Array[Char] = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ012345679".toCharArray
  /** An instance of secure random to ensure randomness is secure. */
  private final val randomizer: SecureRandom = new SecureRandom
  /** The maximum length the random string can be. */

  def getMinLength: Int = {
    return this.DEFAULT_MAX_RANDOM_LENGTH
  }

  def getMaxLength: Int = {
    return this.DEFAULT_MAX_RANDOM_LENGTH
  }

  def getNewString: String = {
    val random: Array[Byte] = getNewStringAsBytes
    convertBytesToString(random)
  }

  def getNewStringAsBytes: Array[Byte] = {
    val random: Array[Byte] = new Array[Byte](this.maxRandomLength)
    this.randomizer.nextBytes(random)
    random
  }

  /**
   * Convert bytes to string, taking into account {@link #PRINTABLE_CHARACTERS}.
   *
   * @param random the random
   * @return the string
   */
  private def convertBytesToString(random: Array[Byte]): String = {
    val output:Array[Char] = (1 to random.length).map{
      i => PRINTABLE_CHARACTERS(Math.abs(random(i-1) % PRINTABLE_CHARACTERS.length))
    }.toArray
    return new String(output)
  }
}
