package models.service.impl;

import com.google.inject.Inject;
import models.ApiAccessToken;
import models.FrontendUser;
import models.repository.api.ApiAccessTokenRepository;
import models.service.api.ApiAccessTokenServiceInterface;
import models.service.impl.generic.ModelService;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by daniel on 19.12.14.
 */
public class ApiAccessTokenService extends ModelService<ApiAccessToken> implements ApiAccessTokenServiceInterface {

    @Inject
    public ApiAccessTokenService(ApiAccessTokenRepository apiAccessTokenRepository) {
        super(apiAccessTokenRepository);
    }

    @Nullable
    protected ApiAccessToken getNewestNonExpiredTokenForFrontendUser(FrontendUser frontendUser) {
        ApiAccessToken newest = null;
        for (ApiAccessToken apiAccessToken : ((ApiAccessTokenRepository) this.modelRepository).findByFrontendUser(frontendUser)) {
            if (newest == null || apiAccessToken.getExpiresAt() > newest.getExpiresAt()) {
                newest = apiAccessToken;
            }
        }
        if (newest != null && newest.getExpiresAt() > System.currentTimeMillis()) {
            return newest;
        }
        return null;
    }

    @Override
    public boolean isValid(String token, FrontendUser frontendUser) {
        checkNotNull(token);
        checkNotNull(frontendUser);

        ApiAccessToken newestToken = this.getNewestNonExpiredTokenForFrontendUser(frontendUser);
        return newestToken != null && newestToken.getToken().equals(token);

    }
}
