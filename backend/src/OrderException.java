/**
 * OrderException.java
 * -----------------------------------------------------
 * A custom CHECKED exception used to represent business-logic
 * errors specific to placing orders — for example, "product not
 * found" or "not enough stock available".
 *
 * We create our own exception class (extending Exception) instead
 * of just using generic RuntimeExceptions so that:
 *  1. The compiler FORCES calling code to handle it (via try-catch
 *     or "throws"), making these errors impossible to accidentally ignore.
 *  2. OrderHandler can catch specifically OrderException and know
 *     it's safe to show the message directly to the user (as a
 *     400 Bad Request), rather than treating it as an unexpected
 *     server crash (500 error).
 * -----------------------------------------------------
 */
public class OrderException extends Exception {

    public OrderException(String message) {
        super(message);
    }
}
