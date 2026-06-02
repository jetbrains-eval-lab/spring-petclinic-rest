package org.springframework.samples.petclinic.repository.springdatajpa;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.samples.petclinic.model.User;
import org.springframework.samples.petclinic.repository.UserRepository;

@Profile("spring-data-jpa")
public interface SpringDataUserRepository extends UserRepository, Repository<User, String>  {

    /**
     * Find a user by username with enabled=true
     * @param username the username to search for
     * @return the enabled user with the given username, or null if none found
     */
    @Query("SELECT u FROM User u WHERE u.username = :username AND u.enabled = true")
    User findEnabledUserByUsername(@Param("username") String username);
}
