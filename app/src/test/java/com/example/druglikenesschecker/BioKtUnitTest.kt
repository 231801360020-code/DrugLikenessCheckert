package com.example.druglikenesschecker

import biokt.DrugMLPredictor
import biokt.MolDescriptors
import biokt.Molecule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BioKtUnitTest {

    @Test
    fun testAspirinBioKtDescriptors() {
        val aspirinSmiles = "CC(=O)Oc1ccccc1C(=O)O"
        val mol = Molecule(aspirinSmiles, name = "Aspirin")

        assertEquals("Aspirin", mol.name)
        assertEquals("C9H8O4", mol.molecularFormula())

        val desc = MolDescriptors.calculate(mol)

        assertTrue("MW should be around 180.16", desc.molecularWeight in 179.0..181.0)
        assertEquals(1, desc.hBondDonors)
        assertEquals(4, desc.hBondAcceptors)
        assertTrue("TPSA should be positive", desc.topologicalPolarSurfaceArea > 0.0)
        assertTrue("Aspirin passes Lipinski", desc.lipinskiPasses)
        assertTrue("Aspirin passes Veber", desc.veberPasses)
        assertTrue("Oral bioavailability should not be blank", desc.admet.oralBioavailability.isNotBlank())
    }

    @Test
    fun testIbuprofenBioKtDescriptors() {
        val ibuprofenSmiles = "CC(C)Cc1ccc(cc1)C(C)C(=O)O"
        val mol = Molecule(ibuprofenSmiles, name = "Ibuprofen")

        assertEquals("Ibuprofen", mol.name)
        val desc = MolDescriptors.calculate(mol)

        assertTrue("MW should be around 206.28", desc.molecularWeight in 205.0..208.0)
        assertTrue("Ibuprofen passes Lipinski", desc.lipinskiPasses)
        assertTrue("Ibuprofen passes Veber", desc.veberPasses)
    }

    @Test
    fun testDrugMLPredictor() {
        val aspirinSmiles = "CC(=O)Oc1ccccc1C(=O)O"
        val mol = Molecule(aspirinSmiles, name = "Aspirin")

        val predictions = DrugMLPredictor.fullAnalysis(mol)

        assertNotNull(predictions["activity"])
        assertNotNull(predictions["toxicity"])
        assertNotNull(predictions["herg_risk"])

        val activityPred = predictions["activity"]!!
        assertTrue("Activity confidence should be > 0", activityPred.confidence > 0.0)
        assertTrue("Activity label should not be blank", activityPred.label.isNotBlank())
    }
}
