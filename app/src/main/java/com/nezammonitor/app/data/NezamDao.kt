package com.nezammonitor.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NezamDao {
    // Cases - sort by year DESC, number ASC
    @Query("SELECT * FROM cases ORDER BY id ASC")
    fun getAllCases(): Flow<List<CaseEntity>>
    
    @Query("SELECT * FROM cases WHERE caseNumber LIKE '%' || :query || '%' OR owner LIKE '%' || :query || '%' ORDER BY id ASC")
    fun searchCases(query: String): Flow<List<CaseEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCases(cases: List<CaseEntity>)
    
    @Delete
    suspend fun deleteCases(cases: List<CaseEntity>)
    
    @Query("DELETE FROM cases")
    suspend fun deleteAllCases()
    
    // Specifications
    @Query("SELECT * FROM specifications WHERE caseNumber = :caseNumber")
    suspend fun getSpecification(caseNumber: String): SpecificationEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpecifications(specs: List<SpecificationEntity>)
    
    @Query("DELETE FROM specifications")
    suspend fun deleteAllSpecifications()
    
    // Engineers
    @Query("SELECT * FROM engineers WHERE caseNumber = :caseNumber")
    suspend fun getEngineersForCase(caseNumber: String): List<EngineerEntity>
    
    @Query("SELECT * FROM engineers ORDER BY id ASC")
    fun getAllEngineers(): Flow<List<EngineerEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEngineers(engineers: List<EngineerEntity>)
    
    @Query("DELETE FROM engineers")
    suspend fun deleteAllEngineers()
    
    // Fees
    @Query("SELECT * FROM fees WHERE caseNumber = :caseNumber")
    suspend fun getFeesForCase(caseNumber: String): List<FeeEntity>
    
    @Query("SELECT * FROM fees ORDER BY id ASC")
    fun getAllFees(): Flow<List<FeeEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFees(fees: List<FeeEntity>)
    
    @Query("DELETE FROM fees")
    suspend fun deleteAllFees()
    
    // Reports
    @Query("SELECT * FROM reports WHERE caseNumber = :caseNumber")
    suspend fun getReportsForCase(caseNumber: String): List<ReportEntity>
    
    @Query("SELECT * FROM reports ORDER BY id ASC")
    fun getAllReports(): Flow<List<ReportEntity>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReports(reports: List<ReportEntity>)
    
    @Query("DELETE FROM reports")
    suspend fun deleteAllReports()
    
    // FollowUp Edits
    @Query("SELECT * FROM specifications ORDER BY id ASC")
    fun getAllSpecifications(): Flow<List<SpecificationEntity>>

    @Query("SELECT * FROM followup_edits ORDER BY caseNumber ASC")
    fun getAllFollowUpEdits(): Flow<List<FollowUpEditEntity>>
    
    @Query("SELECT * FROM followup_edits WHERE caseNumber = :caseNumber")
    suspend fun getFollowUpEdit(caseNumber: String): FollowUpEditEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFollowUpEdit(edit: FollowUpEditEntity)
    
    @Delete
    suspend fun deleteFollowUpEdit(edit: FollowUpEditEntity)
    
    @Query("DELETE FROM followup_edits")
    suspend fun deleteAllFollowUpEdits()
    
    // Stats
    @Query("SELECT COUNT(*) FROM cases")
    suspend fun getCasesCount(): Int
    
    @Query("SELECT COUNT(*) FROM engineers")
    suspend fun getEngineersCount(): Int
    
    @Query("SELECT COUNT(*) FROM fees")
    suspend fun getFeesCount(): Int
    
    @Query("SELECT COUNT(*) FROM reports")
    suspend fun getReportsCount(): Int

    // FeeReceipts v3 — دریافتی‌ها
    @Query("SELECT * FROM fee_receipts ORDER BY receiptDate DESC, id DESC")
    fun getAllFeeReceipts(): kotlinx.coroutines.flow.Flow<List<FeeReceiptEntity>>
    @Query("SELECT * FROM fee_receipts WHERE id = :id")
    suspend fun getFeeReceipt(id: Long): FeeReceiptEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeeReceipts(list: List<FeeReceiptEntity>)
    @Query("DELETE FROM fee_receipts")
    suspend fun deleteAllFeeReceipts()
}
