import { HttpResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { ActivatedRouteSnapshot, Resolve } from '@angular/router';
import { SystemNotification } from 'app/core/shared/entities/system-notification.model';
import { filter, map } from 'rxjs/operators';
import { SystemNotificationService } from 'app/core/notification/system-notification/system-notification.service';

@Injectable({ providedIn: 'root' })
export class SystemNotificationManagementResolve implements Resolve<SystemNotification> {
    private service = inject(SystemNotificationService);

    /**
     * Resolves the route and initializes system notification from id route param
     * @param route
     */
    resolve(route: ActivatedRouteSnapshot) {
        if (route.params['id']) {
            return this.service.find(parseInt(route.params['id'], 10)).pipe(
                filter((response: HttpResponse<SystemNotification>) => response.ok),
                map((response: HttpResponse<SystemNotification>) => response.body!),
            );
        }
        return new SystemNotification();
    }
}
