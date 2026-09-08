package com.qr_ordering_system.app.identity.repository;

import com.qr_ordering_system.app.identity.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository <User,Long> {

    @Query (
            value = """
                    select * from users
                    where email = :email
                    and is_deleted = false
                    """,
            nativeQuery = true
    )
    Optional<User> findByEmailForLogin(@Param("email") String email);

}
