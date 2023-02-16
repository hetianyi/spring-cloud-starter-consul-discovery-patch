package com.github.hetianyi.spring.cloud.consul.patch;

import com.ecwid.consul.v1.OperationException;
import org.springframework.cloud.consul.discovery.ReregistrationPredicate;

/**
 * @author hetianyi
 */
public class AlwaysReregistrationPredicate implements ReregistrationPredicate {
    @Override
    public boolean isEligible(OperationException e) {
        return true;
    }
}
