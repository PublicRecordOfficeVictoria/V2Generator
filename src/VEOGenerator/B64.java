package VEOGenerator;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/***************************************************************
 *
 * B 6 4
 *
 * Andrew Waugh (andrew.waugh@dvc.vic.gov.au)
 * Copyright 2006 PROV
 *
 *
 **************************************************************/

/**
 * This class encapsulates routines to encode and decode from Base64.
 * Base64 is defined in RFC 2045 Multipurpose Internet Mail Extensions
 * (MIME) Part One: Format of Internet Message Bodies, section 6.8.
 * <p>
 * For speed, the routines directly encode and decode into UTF-8.
 **/
public class B64 {

/**
 * This array maps how each 6 bits are mapped to characters
 **/
private static final byte[] CHAR_MAP_ENC = {
	// A     B     C     D     E     F     G     H
	0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48,
	// I     J     K     L     M     N     O     P
	0x49, 0x4a, 0x4b, 0x4c, 0x4d, 0x4e, 0x4f, 0x50,
	// Q     R     S     T     U     V     W     X
	0x51, 0x52, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58,
	// Y     Z     a     b     c     d     e     f     
	0x59, 0x5a, 0x61, 0x62, 0x63, 0x64, 0x65, 0x66,
	// g     h     i     j     k     l     m     n
	0x67, 0x68, 0x69, 0x6a, 0x6b, 0x6c, 0x6d, 0x6e,
	// o     p     q     r     s     t     u     v
	0x6f, 0x70, 0x71, 0x72, 0x73, 0x74, 0x75, 0x76,
	// w     x     y     z     0     1     2     3
	0x77, 0x78, 0x79, 0x7a, 0x30, 0x31, 0x32, 0x33,
	// 4     5     6     7     8     9     +     /
	0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x2b, 0x2f};

/**
 * Constructor.
 */
public B64() {}

/**
 * Convert the bytes from an InputStream into UTF-8 encoded Base64. The
 * resulting octets are directly written to a VEO. Lines are broken every
 * 76 characters.
 *
 * @param in	the InputStream from which the binary bytes are read
 * @param veo	the VEOGenerator to which the UTF-8 encoded Base64 is written
 * @throws java.io.IOException
 * @throws VEOError
 */
public void toBase64(InputStream in, VEOGenerator veo)
	throws IOException, VEOError {
	int i, length;
	byte bin[] = new byte[3];
	byte b[] = new byte[4];
	byte bout[] = new byte[78];
	byte lf[] = {0x0a, 0x0d};

	i = 0;
	while ((length = in.read(bin)) != -1) {
		if (length == 1) { bin[1] = 0; bin[2] = 0; }
		if (length == 2) { bin[2] = 0; }

		// bit shuffle to map the 3 input bytes to 4 output bytes
		b[0] = (byte)((bin[0]&0xFC)>>2);
		b[1] = (byte)(((bin[0]&0x03)<<4)|((bin[1]&0xF0)>>4));
		b[2] = (byte)(((bin[1]&0x0F)<<2)|((bin[2]&0xC0)>>6));
		b[3] = (byte)(bin[2]&0x3F);

		// convert to characters, making sure that the partially filled buffers
		// are handled
		b[0] = CHAR_MAP_ENC[b[0]];
		b[1] = CHAR_MAP_ENC[b[1]];
		if (length > 1)
			b[2] = CHAR_MAP_ENC[b[2]];
		else
			b[2] = 0x3d; // '=' character
		if (length > 2)
			b[3] = CHAR_MAP_ENC[b[3]];
		else
			b[3] = 0x3d; // '=' character
		
		bout[i] = b[0];
		bout[i+1] = b[1];
		bout[i+2] = b[2];
		bout[i+3] = b[3];
		i = i+4;

		// output line feed/carriage return after 19 conversions
		if (i == 76) {
			bout[i] = 0x0d;
			bout[i+1] = 0x0a;
			veo.outputDataToVeo(bout, 0, i+2);
			i = 0;
		}
	}

	// output the final line
	if (i != 0) {
		bout[i] = 0x0d;
		bout[i+1] = 0x0a;
		veo.outputDataToVeo(bout, 0, i+2);
	}
}

/**
 * Convert 24 bits (in a 3 byte array) into UTF-8 encoded Base64 (in a 4 byte
 * array).
 *
 * @param	bin	the 3 input bytes (24 bits)
 * @param       length  length of input array
 * @param	bout	the 4 output bytes
 */
public void toBase64(byte[] bin, int length, byte[] bout) {

	// if input buffer is partially filled, make sure remainder is zeroed
	if (length == 1) { bin[1] = 0; bin[2] = 0; }
	if (length == 2) { bin[2] = 0; }

	// bit shuffle to map the 3 input bytes to 4 output bytes
	bout[0] = (byte)((bin[0]&0xFC)>>2);
	bout[1] = (byte)(((bin[0]&0x03)<<4)|((bin[1]&0xF0)>>4));
	bout[2] = (byte)(((bin[1]&0x0F)<<2)|((bin[2]&0xC0)>>6));
	bout[3] = (byte)(bin[2]&0x3F);

	// convert to characters, making sure that the partially filled buffers
	// are handled
	bout[0] = CHAR_MAP_ENC[bout[0]];
	bout[1] = CHAR_MAP_ENC[bout[1]];
	if (length > 1)
		bout[2] = CHAR_MAP_ENC[bout[2]];
	else
		bout[2] = 0x3d; // '=' character
	if (length > 2)
		bout[3] = CHAR_MAP_ENC[bout[3]];
	else
		bout[3] = 0x3d; // '=' character
}

/**
 * This encodes arbitrary length byte array to a ByteBuffer containing
 * UTF-8 encoded Base64.
 *
 * @param bin array of bytes to encode
 * @return a ByteBuffer containing the Base64 encoded bytes in UTF-8
 */
public ByteBuffer toBase64(byte[] bin) {
	byte b[] = new byte[3], bout[] = new byte[4];
	int i, j, length;
	ByteBuffer bb;

	bb = ByteBuffer.allocate(10000);
	j = 1;
	for (i=0; i<bin.length; i=i+3) {
		length = 3;
		try {
			b[2] = bin[i+2];
		} catch (ArrayIndexOutOfBoundsException e) {
			length = 2;
		}
		try {
			b[1] = bin[i+1];
		} catch (ArrayIndexOutOfBoundsException e) {
			length = 1;
		}
		b[0] = bin[i];
		toBase64(b, length, bout);
		bb.put(bout);

		// if 72 characters have been put on a line, output
		// carriage return and line feed
		if (j%18 == 0) {
			bb.put((byte) 0x0d); 
			bb.put((byte) 0x0a); 
			j = 1;
		} else
			j++;
	}
	bb.limit(bb.position());
	bb.rewind();
	return bb;
}

private static final byte[] CHAR_MAP_DEC = {
	64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, // 00
	64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, // 10
	64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 62, 64, 64, 64, 63, // 20
	52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 64, 64, 64, 00, 64, 64, // 30
	64,  0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14, // 40
	15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 64, 64, 64, 64, 64, // 50
	64, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, // 60
	41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 64, 64, 64, 64, 64, // 70
	64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, // 80
	64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, // 90
	64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, // a0
	64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, // b0
	64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, // c0
	64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, // d0
	64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, // e0
	64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64, 64  // f0
};

/**
 * Decode the data in 4 bytes of UTF-8 into 24 bits in 3 bytes
 *
 * @param	bin	the 4 input bytes
 * @param	bout	the 3 output bytes (24 bits)
 * @return      number of valid bytes in output array
 */
public int fromBase64(byte[] bin, byte[] bout) {
	int length;
	byte[] b;

	b = new byte[4];

	length = 3;
	if (bin[3] == 0x3d) length = 2;
	if (bin[2] == 0x3d) length = 1;
	b[0] = CHAR_MAP_DEC[bin[0]];
	b[1] = CHAR_MAP_DEC[bin[1]];
	b[2] = CHAR_MAP_DEC[bin[2]];
	b[3] = CHAR_MAP_DEC[bin[3]];
	bout[0] = (byte)(((b[0]&0x3f)<<2)|((b[1]&0x30)>>4));
	bout[1] = (byte)(((b[1]&0x0f)<<4)|((b[2]&0x3c)>>2));
	bout[2] = (byte)(((b[2]&0x03)<<6)|(b[3]&0x3f));
	return length;
}

/**
 * Test program to test encoding and decoding.
 * @param args command line params
 */
public static void main(String args[]) {
	B64 b64;
	byte[] bin, bout;
	int i, j, k;
	int length;

	b64 = new B64();
	bin = new byte[3];
	bout = new byte[4];
	for (i=0; i<256; i++) {
		System.out.print(i+" - - ");
		bin[0] = (byte) (i & 0xff);
		bin[1] = -1;
		bin[2] = -1;
		b64.toBase64(bin, 1, bout);
		System.out.print(bout[0]+" "+bout[1]+" "+bout[2]+" "+bout[3]);
		length = b64.fromBase64(bout, bin);
		if (length == 1 && bin[0] == (byte) (i & 0xff))
			System.out.println("Passed");
		else {
			System.out.println("Failed");
			System.exit(-1);
		}
	}
	for (i=0; i<256; i++) {
		for (j=0; j<256; j++) {
			System.out.print(i+" "+j+" - ");
			bin[0] = (byte) (i & 0xff);
			bin[1] = (byte) (j & 0xff);
			bin[2] = -1;
			b64.toBase64(bin, 2, bout);
			System.out.print(bout[0]+" "+bout[1]+" "+bout[2]+" "+bout[3]);
			length = b64.fromBase64(bout, bin);
			System.out.print(bin[0]+" "+bin[1]+" "+bin[2]);
			if (length == 2 && bin[0] == (byte)(i&0xff) && bin[1] == (byte)(j&0xff))
				System.out.println("Passed");
			else {
				System.out.println("Failed");
				System.exit(-1);
			}
		}
	}
	for (i=0; i<256; i++) {
		for (j=0; j<256; j++) {
			for (k=0; k<256; k++) {
				System.out.print(i+" "+j+" "+k+": ");
				bin[0] = (byte) (i&0xff);
				bin[1] = (byte) (j&0xff);
				bin[2] = (byte) (k&0xff);
				b64.toBase64(bin, 3, bout);
				length = b64.fromBase64(bout, bin);
				if (length == 3 && bin[0] == (byte)(i&0xff) && bin[1] == (byte)(j&0xff) && bin[2] == (byte)(k&0xff))
					System.out.println("Passed");
				else {
					System.out.println("Failed");
					System.exit(-1);
				}
			}
		}
	}
}
}
