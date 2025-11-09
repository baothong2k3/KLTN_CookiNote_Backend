package fit.kltn_cookinote_backend.repositories;

import fit.kltn_cookinote_backend.entities.DailyMenu;
import fit.kltn_cookinote_backend.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface DailyMenuRepository extends JpaRepository<DailyMenu, Long> {

    /**
     * Lấy thực đơn đã lưu theo User và Ngày, đồng thời tải sẵn Recipe và User (chủ sở hữu) của Recipe đó.
     */
    @Query("SELECT dm FROM DailyMenu dm " +
            "JOIN FETCH dm.recipe r " +
            "LEFT JOIN FETCH r.user " + // Tải chủ sở hữu của Recipe
            "WHERE dm.user = :user AND dm.menuDate = :date " +
            "ORDER BY dm.mealType ASC")
    List<DailyMenu> findByUserAndMenuDateWithRecipe(@Param("user") User user, @Param("date") LocalDate date);

    /**
     * Lấy thực đơn (chỉ ID) theo UserId và Ngày.
     * Dùng để kiểm tra sự tồn tại hoặc lấy ID món của ngày hôm qua.
     */
    @Query("SELECT dm FROM DailyMenu dm " +
            "WHERE dm.user.userId = :userId AND dm.menuDate = :date " +
            "ORDER BY dm.mealType ASC")
    List<DailyMenu> findByUserIdAndMenuDate(@Param("userId") Long userId, @Param("date") LocalDate date);
}