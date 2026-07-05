import axios, {
  type AxiosInstance,
  type AxiosRequestConfig,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios';
import type { ApiResponse } from '@gaokao/shared-types';

function generateTraceId(): string {
  return `${Date.now()}-${Math.random().toString(36).substring(2, 11)}`;
}

export class HttpClient {
  private instance: AxiosInstance;

  constructor(baseURL: string = '/api/v1') {
    this.instance = axios.create({
      baseURL,
      timeout: 30000,
      withCredentials: true,
      headers: {
        'Content-Type': 'application/json',
      },
    });

    this.instance.interceptors.request.use(
      (config: InternalAxiosRequestConfig) => {
        const traceId = generateTraceId();
        config.headers.set('X-Trace-Id', traceId);
        return config;
      },
      (error) => Promise.reject(error)
    );

    this.instance.interceptors.response.use(
      (response: AxiosResponse<ApiResponse<unknown>>) => {
        const apiResponse = response.data;
        if (apiResponse.code !== 0) {
          console.warn(
            `[API] ${response.config.url} | traceId=${apiResponse.traceId} | code=${apiResponse.code} | ${apiResponse.message}`
          );
          return Promise.reject(new ApiError(apiResponse.code, apiResponse.message, apiResponse.traceId));
        }
        return response;
      },
      (error) => {
        if (error.response?.status === 401) {
          // Only redirect if not already on login page
          if (!window.location.pathname.includes('/login')) {
            window.location.href = '/login';
          }
        }
        console.error(`[API] ${error.config?.url} | ${error.message}`);
        return Promise.reject(error);
      }
    );
  }

  async get<T>(url: string, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
    const response = await this.instance.get<ApiResponse<T>>(url, config);
    return response.data;
  }

  async post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
    const response = await this.instance.post<ApiResponse<T>>(url, data, config);
    return response.data;
  }

  async put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
    const response = await this.instance.put<ApiResponse<T>>(url, data, config);
    return response.data;
  }

  async patch<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
    const response = await this.instance.patch<ApiResponse<T>>(url, data, config);
    return response.data;
  }

  async delete<T>(url: string, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
    const response = await this.instance.delete<ApiResponse<T>>(url, config);
    return response.data;
  }
}

export class ApiError extends Error {
  constructor(
    public code: number,
    message: string,
    public traceId: string
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

export const httpClient = new HttpClient();
export const adminHttpClient = new HttpClient('/api/admin/v1');
