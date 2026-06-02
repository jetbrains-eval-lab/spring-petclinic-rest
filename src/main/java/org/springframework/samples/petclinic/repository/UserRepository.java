package org.springframework.samples.petclinic.repository;

import org.springframework.dao.DataAccessException;
import org.springframework.samples.petclinic.model.User;

public interface UserRepository {

    void save(User user) throws DataAccessException;

    /**
     * Find a user by username with enabled=true
     * @param username the username to search for
     * @return the enabled user with the given username, or null if none found
     * @throws DataAccessException
     */
    User findEnabledUserByUsername(String username) throws DataAccessException;
}
