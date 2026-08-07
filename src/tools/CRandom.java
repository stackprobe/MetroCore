package tools;

import java.security.SecureRandom;

/**
 * 暗号論的擬似乱数生成器 (CSPRNG)
 *
 * java.util.Random ではなく SecureRandom を使用する。
 * シードは SecureRandom 側に委譲する。
 */
public class CRandom {
	private final SecureRandom _random = new SecureRandom();

	/**
	 * 0 ～ modulo - 1 の乱数を返す。
	 *
	 * @param modulo 上限値 + 1
	 * @return 0 ～ modulo - 1
	 */
	public int getInt(int modulo) {
		return _random.nextInt(modulo);
	}

	/**
	 * minval ～ maxval の範囲の乱数を返す。
	 * 両端を含む。
	 *
	 * @param minval 最小値
	 * @param maxval 最大値
	 * @return minval ～ maxval
	 */
	public int getRange(int minval, int maxval) {
		return _random.nextInt((maxval - minval) + 1) + minval;
	}
}
