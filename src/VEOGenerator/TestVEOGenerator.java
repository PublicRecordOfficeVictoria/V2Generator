package VEOGenerator;

import java.io.File;

/***************************************************************
 *
 * V E O   G E N E R A T O R
 *
 * This class generates VEOs from a template and data
 *
 * Andrew Waugh (andrew.waugh@dvc.vic.gov.au)
 * Copyright 2006 PROV
 *
 **************************************************************/


/**
 * This class exercises the VEO Generator
 */
public class TestVEOGenerator {
	File encDir;	// encoding template directory
	File veo;
	PFXUser signer;	// user
	VEOGenerator vg;
	Fragment rMeta, dMeta, fMeta;
	TableDataSource tds;

/**
 * Default constructor.
 */
private TestVEOGenerator(String[] args) {
	encDir = new File("testData/encDirectory");
	veo = new File("Testveo.veo");
	try {
		vg = new VEOGenerator(encDir, args);
		signer = new PFXUser("testData/signer.pfx", "Ag0nc1eS");
		tds = new TableDataSource(new File("testData/dataSource.txt"));
		rMeta = Fragment.parseTemplate(new File("testData/recMeta.txt"), args);
		dMeta = Fragment.parseTemplate(new File("testData/dMeta.txt"), args);
		fMeta = Fragment.parseTemplate(new File("testData/fMeta.txt"), args);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
}

/**
 * test PFXUser
 */
private void testPFXUser() {
	int l;
	PFXUser user;

	System.out.println("*PFXUser(): Junk file name");
	try {
		user = new PFXUser("signe3r.pfx", "Ag0nc1eS");
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
	System.out.println("*PFXUser(): incorrect password");
	try {
		user = new PFXUser("testData/signer.pfx", "Agonc1eS");
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}

	// suceed in opening keystore
	user = null;
	try {
		user = new PFXUser("testData/signer.pfx", "Ag0nc1eS");
	} catch (VEOError e) {
		System.out.println("Panic1: "+e.getMessage());
	}
	System.out.println("*PFXUser(): ask for certificate -1");
	try {
		user.getCertificate(-1);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
	try {
		user.getX509Certificate(-1);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
	l = user.getCertificateChainLength();
	System.out.println("*PFXUser(): ask for certificate length");
	try {
		user.getCertificate(l);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
	try {
		user.getX509Certificate(l);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
}

/**
 * Test VEO Generator constructor
 */
private void testVEOGenerator() {
	String args[] = {"one", "two"};

	System.out.println("*veoGenerator(): unknown encoding directory");
	try {
		vg = new VEOGenerator(new File("invalid"), args);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
}

/**
 * Test the parser
 */
private void testParseTemplate() {
	String[] args = {"Argument0", "Argument1"};
	String[] data = {"testdata\\subsData.pdf", "testdata\\subsData.pdf",
			 "testdata\\utf8.txt", "testdata\\xml.xml",
			 "testdata\\Column5"};
	Fragment f = null;
	ArrayDataSource ds;

	System.out.println("*parseTemplate(): unknown file");
	try {
		Fragment.parseTemplate(new File("recMeta.txt"), args);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
	System.out.println("*parseTemplate(): file is a directory");
	try {
		Fragment.parseTemplate(new File("testdata"), args);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
	System.out.println("parseTemplate(): test substitutions");
	try {
		f = Fragment.parseTemplate(new File("testdata/substitution.txt"), args);
		System.out.println(f.toString());
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
	try {
		ds = new ArrayDataSource(data);
		vg.startVEO(new File("testdata/subsTest.veo"), 1, 1);
		vg.addSignatureBlock(signer);
		vg.addLockSignatureBlock(1, signer);
		vg.addFile(f, ds);
		vg.endVEO();
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
}

/**
 * Test startVEO()
 */
private void testStartVEO() {
	VEOGenerator v;
	String[] args = {"VEOGenerator"};

	v = null;
	try {
		v = new VEOGenerator(new File("testdata"), args);
	} catch (VEOError e) {
		System.out.println("Panic1: "+e.getMessage());
	}
	System.out.println("*startVEO(): invalid file");
	try {
		v.startVEO(new File("testdata"), 1, 1);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
	System.out.println("*startVEO(): invalid revision no");
	try {
		v.startVEO(new File("test.veo"), 1, -1);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
	System.out.println("*startVEO(): starting twice");
	try {
		v.startVEO(new File("test.veo"), 1, 1);
		v.startVEO(new File("test.veo"), 1, 1);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
}

/**
 * Test addSignatureBlock()
 */
private void testAddSignatureBlock() {
	VEOGenerator v;
	String[] args = {"VEOGenerator"};

	v = null;
	try {
		v = new VEOGenerator(new File("testdata/encDirectory"), args);
	} catch (VEOError e) {
		System.out.println("Panic1: "+e.getMessage());
	}
	System.out.println("*testAddSignatureBlock(): called before startVEO");
	try {
		v.addSignatureBlock(signer);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}

	try {
		v.startVEO(new File("test.veo"), 1, 1);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
	System.out.println("*testAddSignatureBlock(): null signer");
	try {
		v.addSignatureBlock(null);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
	System.out.println("*testAddSignatureBlock(): called after addLockSigBlock()");
	try {
		v.addSignatureBlock(signer);
		v.addLockSignatureBlock(1, signer);
		v.addSignatureBlock(signer);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
}

/**
 * Test addLockSignatureBlock()
 */
private void testAddLockSignatureBlock() {
	VEOGenerator v;
	String[] args = {"VEOGenerator"};

	v = null;
	try {
		v = new VEOGenerator(new File("testdata/encDirectory"), args);
		v.startVEO(new File("test.veo"), 1, 1);
	} catch (VEOError e) {
		System.out.println("Panic1: "+e.getMessage());
	}
	System.out.println("*testLockAddSignatureBlock(): called before addSignatureBlock");
	try {
		v.addLockSignatureBlock(1, signer);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}

	try {
		v.addSignatureBlock(signer);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
	System.out.println("*testLockAddSignatureBlock(): 0 sig block ref");
	try {
		v.addLockSignatureBlock(0, signer);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
	System.out.println("*testLockAddSignatureBlock(): 2 sig block ref");
	try {
		v.addLockSignatureBlock(2, signer);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
	System.out.println("*testLockAddSignatureBlock(): null signer");
	try {
		v.addLockSignatureBlock(1, null);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
	System.out.println("*testLockAddSignatureBlock(): called twice");
	try {
		v.addLockSignatureBlock(1, signer);
		v.addLockSignatureBlock(1, signer);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
	System.out.println("*testLockAddSignatureBlock(): called after startRecord()");
	try {
		v.startRecord(rMeta, tds);
		v.addLockSignatureBlock(1, signer);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
}

/**
 * Test startRecord()
 */
private void testStartRecord() {
	VEOGenerator v;
	String[] args = {"VEOGenerator"};

	v = null;
	try {
		v = new VEOGenerator(new File("testdata/encDirectory"), args);
		v.startVEO(new File("test.veo"), 1, 1);
		v.addSignatureBlock(signer);
	} catch (VEOError e) {
		System.out.println("Panic1: "+e.getMessage());
	}
	System.out.println("*testStartRecord(): called before addLockSignatureBlock");
	try {
		v.startRecord(rMeta, tds);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}

	try {
		v.addLockSignatureBlock(1, signer);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
	System.out.println("*testStartRecord(): null template");
	try {
		v.startRecord(null, tds);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
	System.out.println("*testStartRecord(): null data");
	try {
		v.startRecord(rMeta, null);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
	System.out.println("*testStartRecord(): called twice");
	try {
		v.startRecord(rMeta, tds);
		v.startRecord(rMeta, tds);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
	System.out.println("*testStartRecord(): called after startDocument()");
	try {
		v.startDocument(dMeta, tds);
		v.startRecord(rMeta, tds);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
}

/**
 * Test startDocument()
 */
private void testStartDocument() {
	VEOGenerator v;
	String[] args = {"VEOGenerator"};

	v = null;
	try {
		v = new VEOGenerator(new File("testdata/encDirectory"), args);
		v.startVEO(new File("test.veo"), 1, 1);
		v.addSignatureBlock(signer);
		v.addLockSignatureBlock(1, signer);
	} catch (VEOError e) {
		System.out.println("Panic1: "+e.getMessage());
	}
	System.out.println("*testStartDocument(): called before startRecord()");
	try {
		v.startDocument(dMeta, tds);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}

	try {
		v.startRecord(rMeta, tds);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
	System.out.println("*testStartDocument(): null template");
	try {
		v.startDocument(null, tds);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
	System.out.println("*testStartDocument(): null data");
	try {
		v.startDocument(dMeta, null);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
	System.out.println("*testStartDocument(): called twice");
	try {
		v.startDocument(dMeta, tds);
		v.startDocument(dMeta, tds);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
	System.out.println("*testStartDocument(): called after addEncoding()");
	try {
		v.addEncoding(new File("testdata/test.pdf"));
		v.startDocument(dMeta, tds);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
	System.out.println("*testStartDocument(): called after endRecord()");
	try {
		v.endDocument();
		v.endRecord();
		v.startDocument(dMeta, tds);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
}

/**
 * Test addEncoding()
 */
private void testAddEncoding() {
	VEOGenerator v;
	String[] args = {"VEOGenerator"};

	v = null;
	try {
		v = new VEOGenerator(new File("testdata/encDirectory"), args);
		v.startVEO(new File("test.veo"), 1, 1);
		v.addSignatureBlock(signer);
		v.addLockSignatureBlock(1, signer);
		v.startRecord(rMeta, tds);
	} catch (VEOError e) {
		System.out.println("Panic1: "+e.getMessage());
	}
	System.out.println("*testAddEncoding(): called before startDocument");
	try {
		v.addEncoding(new File("testdata/test.pdf"));
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}

	try {
		v.startDocument(dMeta, tds);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
	System.out.println("*testAddEncoding(): null file");
	try {
		v.addEncoding(null);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
	System.out.println("*testAddEncoding(): non-existant file");
	try {
		v.addEncoding(new File("testdata/nonExistant.pdf"));
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
	System.out.println("*testAddEncoding(): directory");
	try {
		v.addEncoding(new File("testdata"));
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
	System.out.println("*testAddEncoding(): called after endDocument()");
	try {
		v.addEncoding(new File("testdata/test.pdf"));
		v.endDocument();
		v.addEncoding(new File("testdata/test.pdf"));
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
}

/**
 * Test endDocument()
 */
private void testEndDocument() {
	VEOGenerator v;
	String[] args = {"VEOGenerator"};

	v = null;
	try {
		v = new VEOGenerator(new File("testdata/encDirectory"), args);
		v.startVEO(new File("test.veo"), 1, 1);
		v.addSignatureBlock(signer);
		v.addLockSignatureBlock(1, signer);
		v.startRecord(rMeta, tds);
		v.startDocument(dMeta, tds);
	} catch (VEOError e) {
		System.out.println("Panic1: "+e.getMessage());
	}
	System.out.println("*testEndDocument(): called before addEncoding");
	try {
		v.endDocument();
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}

	try {
		v.addEncoding(new File("testdata/test.pdf"));
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
	System.out.println("*testEndDocument(): called twice");
	try {
		v.endDocument();
		v.endDocument();
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
	System.out.println("*testEndDocument(): called after endRecord()");
	try {
		v.endRecord();
		v.endDocument();
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
}

/**
 * Test endRecord()
 */
private void testEndRecord() {
	VEOGenerator v;
	String[] args = {"VEOGenerator"};

	v = null;
	try {
		v = new VEOGenerator(new File("testdata/encDirectory"), args);
		v.startVEO(new File("test.veo"), 1, 1);
		v.addSignatureBlock(signer);
		v.addLockSignatureBlock(1, signer);
		v.startRecord(rMeta, tds);
		v.startDocument(dMeta, tds);
		v.addEncoding(new File("testdata/test.pdf"));
	} catch (VEOError e) {
		System.out.println("Panic1: "+e.getMessage());
	}
	System.out.println("*testEndRecord(): called before endDocument()");
	try {
		v.endRecord();
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}

	try {
		v.endDocument();
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
	System.out.println("*testEndRecord(): called twice");
	try {
		v.endRecord();
		v.endRecord();
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
	System.out.println("*testEndRecord(): called after endVEO()");
	try {
		v.endVEO();
		v.endRecord();
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
}

/**
 * Test endVEO()
 */
private void testEndVEO() {
	VEOGenerator v;
	String[] args = {"VEOGenerator"};

	v = null;
	try {
		v = new VEOGenerator(new File("testdata/encDirectory"), args);
		v.startVEO(new File("test.veo"), 1, 1);
		v.addSignatureBlock(signer);
		v.addLockSignatureBlock(1, signer);
		v.startRecord(rMeta, tds);
		v.startDocument(dMeta, tds);
		v.addEncoding(new File("testdata/test.pdf"));
		v.endDocument();
	} catch (VEOError e) {
		System.out.println("Panic1: "+e.getMessage());
	}
	System.out.println("*testEndVEO(): called before endRecord()");
	try {
		v.endVEO();
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}

	try {
		v.endRecord();
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
	System.out.println("*testEndVEO(): called twice");
	try {
		v.endVEO();
		v.endVEO();
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
}

/**
 * Test addFile()
 */
private void testAddFile() {
	VEOGenerator v;
	String[] args = {"VEOGenerator"};

	v = null;
	try {
		v = new VEOGenerator(new File("testdata/encDirectory"), args);
		v.startVEO(new File("testFile.veo"), 1, 1);
		v.addSignatureBlock(signer);
	} catch (VEOError e) {
		System.out.println("Panic1: "+e.getMessage());
	}
	System.out.println("*testAddFile(): called before addLockSignatureBlock");
	try {
		v.addFile(fMeta, tds);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}

	try {
		v.addLockSignatureBlock(1, signer);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
	System.out.println("*testAddFile(): null template");
	try {
		v.startRecord(null, tds);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
	System.out.println("*testAddFile(): null data");
	try {
		v.startRecord(rMeta, null);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
	System.out.println("*testAddFile(): called twice");
	try {
		v.addFile(fMeta, tds);
		v.addFile(fMeta, tds);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
	System.out.println("*testAddFile(): called after endVEO()");
	try {
		v.endVEO();
		v.addFile(fMeta, tds);
	} catch (VEOError e) {
		System.out.println(e.getMessage());
	}
}

/**
 * Test multiple documents & signers
 */
private void testMultiple() {
	TableDataSource tds;

	try {
		tds = new TableDataSource(new File("testdata\\dataSource.txt"));
		vg.startVEO(new File("testdata\\multiple.veo"), 1, 1);
		vg.addSignatureBlock(signer);
		vg.addSignatureBlock(signer);
		vg.addLockSignatureBlock(2, signer);
		vg.startRecord(rMeta, tds);
		tds.getNextRow();
		vg.startDocument(dMeta, tds);
		tds.getNextRow();
		vg.addEncoding(new File("testdata\\test.pdf"));
		vg.addEncoding(new File("testdata\\subsData.pdf"));
		vg.endDocument();
		vg.startDocument(dMeta, tds);
		tds.getNextRow();
		vg.addEncoding(new File("testdata\\test.pdf"));
		vg.endDocument();
		vg.endRecord();
		vg.endVEO();
	} catch (VEOError ve) {
		System.out.println(ve.getMessage());
	}
}

/**
 * Test main program
 */
public static void main (String args[]) {
	TestVEOGenerator tvg;
	VEOGenerator vg;

	tvg = new TestVEOGenerator(args);
	tvg.testPFXUser();
	tvg.testVEOGenerator();
	tvg.testParseTemplate();

	tvg.testStartVEO();
	tvg.testAddSignatureBlock();
	tvg.testAddLockSignatureBlock();
	tvg.testStartRecord();
	tvg.testStartDocument();
	tvg.testAddEncoding();
	tvg.testEndDocument();
	tvg.testEndRecord();
	tvg.testEndVEO();

	tvg.testAddFile();

	tvg.testMultiple();

	/*
	try {
		vg.startVEO(veo, 1, 1);
		vg.addSignatureBlock(user);
		vg.addLockSignatureBlock(1, user);
		vg.startRecord(recMeta, tds);
		tds.getNextDocument();
		vg.startDocument(docMeta, tds);
		enc = new File("testData/test.pdf");
		tds.getNextEncoding();
		vg.addEncoding(enc);
		vg.endDocument();
		vg.endRecord();
		vg.endVEO();
	} catch (VEOError ve) {
		System.out.println(ve.getMessage());
	}
	*/
}
}
