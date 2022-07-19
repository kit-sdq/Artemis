import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

type JwtToken = {
    id_token: string;
};

@Injectable({ providedIn: 'root' })
export class PersonalAccessTokenService {
    private tokenUrl = SERVER_API_URL + 'api/personal-access-token';
    private lifeTimeUrl = SERVER_API_URL + 'api/personal-access-token/maximum-lifetime';

    constructor(private http: HttpClient) {}

    getTokenMaxLifetimeDays(): Observable<number> {
        return this.http.get<number>(this.lifeTimeUrl);
    }

    generateNewToken(lifetimeDays: number): Observable<string> {
        return this.http.post<JwtToken>(this.tokenUrl, lifetimeDays).pipe(map((resp: JwtToken) => resp['id_token']));
    }
}
