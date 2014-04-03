package org.kframework.backend.java.builtins;

import org.kframework.backend.java.kil.BuiltinList;
import org.kframework.backend.java.kil.Term;
import org.kframework.backend.java.kil.Token;
import org.kframework.backend.java.symbolic.Matcher;
import org.kframework.backend.java.symbolic.Transformer;
import org.kframework.backend.java.symbolic.Unifier;
import org.kframework.backend.java.symbolic.Visitor;
import org.kframework.backend.java.util.Utils;
import org.kframework.kil.ASTNode;

import java.math.BigInteger;
import java.util.List;


/**
 * Abstract class representing a bit vector (and integer on an arbitrary but fixed number of bits).
 * @author AndreiS
 *
 * @see include/builtins/mint.k
 */
public abstract class BitVector<T extends Number> extends Token {

    public static final String SORT_NAME = "MInt";

    /**
     * Integer value wrapped by this BitVector. The signed value and the unsigned value of this
     * BitVector are guaranteed to be equal with {@code value} only on the last {@code bitwidth}
     * bits.
     *
     * @see #signedValue()
     * @see #unsignedValue()
     */
    protected final T value;
    /**
     * The bit width on which this integer is represented.
     */
    protected final int bitwidth;

    protected BitVector(T value, int bitwidth) {
        this.value = value;
        this.bitwidth = bitwidth;
    }

    /**
     * Returns a {@code BitVector} representation of the given big integer value on the given
     * bit width.
     */
    public static BitVector of(BigInteger value, int bitwidth) {
        assert bitwidth > 0;

        switch (bitwidth) {
            case Integer.SIZE:
                return Int32Token.of(value.intValue());
            default:
                return BigIntegerBitVector.of(value, bitwidth);
        }
    }

    /**
     * Returns a {@code BitVector} representation of the given long value on the given bit width.
     */
    public static BitVector of(long value, int bitwidth) {
        assert bitwidth > 0;

        switch (bitwidth) {
            case Integer.SIZE:
                return Int32Token.of(Long.valueOf(value).intValue());
            default:
                return BigIntegerBitVector.of(BigInteger.valueOf(value), bitwidth);
        }
    }

    /**
     * Returns the bit width of this BitVector.
     */
    public int bitwidth() {
        return bitwidth;
    }

    /**
     * Returns true if this BitVector is zero.
     */
    public abstract boolean isZero();

    /**
     * Returns an {@code BigInteger} representation of the signed value of this BitVector.
     */
    public abstract BigInteger signedValue();

    /**
     * Returns an {@code BigInteger} representation of the unsigned value of this BitVector.
     */
    public abstract BigInteger unsignedValue();

    /**
     * Returns a {@code String} representation of the sort of this BitVector.
     */
    @Override
    public String sort() {
        return BitVector.SORT_NAME;
    }

    /**
     * Returns a {@code String} representation of the (uninterpreted) value of this BitVector.
     */
    @Override
    public String value() {
        return bitwidth + "'" + value.toString();
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof BitVector)) {
            return false;
        }

        BitVector bitVector = (BitVector) object;
        return value.equals(bitVector.value) && bitwidth == bitVector.bitwidth;
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = 1;
            hashCode = hashCode * Utils.HASH_PRIME + value.hashCode();
            hashCode = hashCode * Utils.HASH_PRIME + bitwidth;
        }
        return hashCode;
    }

    @Override
    public void accept(Unifier unifier, Term pattern) {
        unifier.unify(this, pattern);
    }

    @Override
    public void accept(Matcher matcher, Term pattern) {
        matcher.match(this, pattern);
    }

    @Override
    public ASTNode accept(Transformer transformer) {
        return transformer.transform(this);
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visit(this);
    }

    public abstract BitVector<T> add(BitVector<T> bitVector);
    public abstract BitVector<T> sub(BitVector<T> bitVector);
    public abstract BitVector<T> mul(BitVector<T> bitVector);

    public abstract BuiltinList sdiv(BitVector<T> bitVector);
    public abstract BuiltinList srem(BitVector<T> bitVector);

    public abstract BitVector<T> udiv(BitVector<T> bitVector);
    public abstract BitVector<T> urem(BitVector<T> bitVector);

    public abstract BuiltinList sadd(BitVector<T> bitVector);
    public abstract BuiltinList uadd(BitVector<T> bitVector);
    public abstract BuiltinList ssub(BitVector<T> bitVector);
    public abstract BuiltinList usub(BitVector<T> bitVector);
    public abstract BuiltinList smul(BitVector<T> bitVector);
    public abstract BuiltinList umul(BitVector<T> bitVector);

    public abstract BitVector<T> shl(IntToken intToken);
    public abstract BitVector<T> ashr(IntToken intToken);
    public abstract BitVector<T> lshr(IntToken intToken);

    public abstract BitVector<T> and(BitVector<T> bitVector);
    public abstract BitVector<T> or(BitVector<T> bitVector);
    public abstract BitVector<T> xor(BitVector<T> bitVector);

    public abstract BoolToken slt(BitVector<T> bitVector);
    public abstract BoolToken ult(BitVector<T> bitVector);
    public abstract BoolToken sle(BitVector<T> bitVector);
    public abstract BoolToken ule(BitVector<T> bitVector);
    public abstract BoolToken sgt(BitVector<T> bitVector);
    public abstract BoolToken ugt(BitVector<T> bitVector);
    public abstract BoolToken sge(BitVector<T> bitVector);
    public abstract BoolToken uge(BitVector<T> bitVector);
    public abstract BoolToken eq(BitVector<T> bitVector);
    public abstract BoolToken ne(BitVector<T> bitVector);

    public abstract List<BitVector> toDigits(int digitBase);

    public static BitVector fromDigits(List<BitVector> digits, int base) {
        assert base > 0;
        assert !digits.isEmpty();

        BigInteger value = BigInteger.ZERO;
        for (BitVector digit : digits) {
            if (digit.bitwidth != base) {
                return null;
            }

            /* value = value * 2^base + digit */
            value = (value.shiftLeft(base)).add(digit.unsignedValue());
        }

        return BitVector.of(value, digits.size() * base);
    }

}
