import { Injectable, effect, inject } from '@angular/core';
import { HttpClient, HttpParameterCodec, HttpParams } from '@angular/common/http';
import { Cacheable } from 'ts-cacheable';
import { Observable, Subject } from 'rxjs';
import { map } from 'rxjs/operators';
import { Theme, ThemeService } from 'app/core/theme/shared/theme.service';

const themeChangedSubject = new Subject<void>();

@Injectable({ providedIn: 'root' })
export class ProgrammingExercisePlantUmlService {
    private resourceUrl = 'api/programming/plantuml';
    private encoder: HttpParameterCodec;

    private readonly themeService = inject(ThemeService);
    private readonly http = inject(HttpClient);

    /**
     * Cacheable configuration
     */

    constructor() {
        this.encoder = new HttpUrlCustomEncoder();
        effect(() => {
            // Apply the theme as soon as the currentTheme changes
            this.themeService.currentTheme();
            themeChangedSubject.next();
        });
    }

    /**
     * Requests the plantuml png file as arraybuffer and converts it to base64.
     * @param plantUml - definition obtained by parsing the README markdown file.
     *
     * Note: we cache up to 100 results in 1 hour so that they do not need to be loaded several time
     */
    @Cacheable({
        /** Cacheable configuration **/
        maxCacheCount: 100,
        maxAge: 3600000, // ms
        slidingExpiration: true,
        cacheBusterObserver: themeChangedSubject, // evict cache on theme change
    })
    getPlantUmlImage(plantUml: string) {
        return this.http
            .get(`${this.resourceUrl}/png`, {
                params: new HttpParams({ encoder: this.encoder }).set('plantuml', plantUml).set('useDarkTheme', this.themeService.currentTheme() === Theme.DARK),
                responseType: 'arraybuffer',
            })
            .pipe(map((res) => this.convertPlantUmlResponseToBase64(res)));
    }

    /**
     * Requests the plantuml svg as string.
     * @param plantUml - definition obtained by parsing the README markdown file.
     *
     * Note: we cache up to 100 results in 1 hour so that they do not need to be loaded several time
     */
    @Cacheable({
        /** Cacheable configuration **/
        maxCacheCount: 100,
        maxAge: 3600000, // ms
        slidingExpiration: true,
        cacheBusterObserver: themeChangedSubject, // evict cache on theme change
    })
    getPlantUmlSvg(plantUml: string): Observable<string> {
        return this.http.get(`${this.resourceUrl}/svg`, {
            params: new HttpParams({ encoder: this.encoder }).set('plantuml', plantUml).set('useDarkTheme', this.themeService.currentTheme() === Theme.DARK),
            responseType: 'text',
        });
    }

    private convertPlantUmlResponseToBase64(res: any): string {
        return Buffer.from(res, 'binary').toString('base64');
    }
}

/**
 * @class HttpUrlCustomEncoder
 * @desc Custom HttpParamEncoder implementation which defaults to using encodeURIComponent to encode params
 */
export class HttpUrlCustomEncoder implements HttpParameterCodec {
    /**
     * Encodes key.
     * @param k - key to be encoded.
     */
    encodeKey(k: string): string {
        return encodeURIComponent(k);
    }

    /**
     * Encodes value.
     * @param v - value to be encoded.
     */
    encodeValue(v: string): string {
        return encodeURIComponent(v);
    }

    /**
     * Decodes key.
     * @param k - key to be decoded.
     */
    decodeKey(k: string): string {
        return decodeURIComponent(k);
    }

    /**
     * Decodes value.
     * @param v - value to be decoded.
     */
    decodeValue(v: string) {
        return decodeURIComponent(v);
    }
}
